package com.templestamp.photo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 기록 — 사진·문장·생각상자·명상 (챕터 6).
 * <p>
 * 여기서 지키는 것 셋.
 * <ol>
 *   <li><b>서버는 사진 바이트를 만지지 않는다.</b> presign 으로 키만 내주고, 제출된 키가 내 것인지만 본다.</li>
 *   <li><b>사찰당 하나.</b> 사진도 문장도 다시 보내면 새 행이 아니라 교체다.</li>
 *   <li><b>없는 것이 정상인 응답이 있다.</b> 여섯 달 전 오늘은 대개 비어 있고, 그때 200 + null 이다.</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
class RecordIntegrationTest {

    private static final String EMAIL_A = "record-a@test.com";
    private static final String EMAIL_B = "record-b@test.com";
    private static final String PASSWORD = "Test1234!";
    /** 내용은 보지 않는다 — 있기만 하면 된다. JPEG 머리 두 바이트로 모양만 맞춘다. */
    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, 0x00, 0x01, 0x02, 0x03};
    private static final String SENTENCE = "처마 끝에 걸린 하늘이 좋아서 한 장 남긴다.";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;

    private String tokenA;
    private String tokenB;
    private long userIdA;
    private long activeSiteId;
    private long draftSiteId;

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
        signup(EMAIL_A, "기록갑");
        signup(EMAIL_B, "기록을");
        tokenA = login(EMAIL_A);
        tokenB = login(EMAIL_B);
        userIdA = jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, EMAIL_A);

        activeSiteId = jdbc.queryForObject(
                "SELECT site_id FROM site WHERE status = 'ACTIVE' ORDER BY site_id LIMIT 1", Long.class);
        draftSiteId = jdbc.queryForObject(
                "SELECT site_id FROM site WHERE status = 'DRAFT' ORDER BY site_id LIMIT 1", Long.class);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    /* ---------------- ① presign ---------------- */

    @Test
    @DisplayName("presign PHOTO/jpeg → 내 번호가 박힌 키가 나온다")
    void presign_returns_key_scoped_to_me() throws Exception {
        JsonNode data = json(mvc.perform(get("/api/uploads/presign?purpose=PHOTO&contentType=image/jpeg")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())).path("data");

        assertThat(data.path("fileKey").asText()).matches("^PHOTO/" + userIdA + "/[0-9a-f-]{36}\\.jpg$");
        assertThat(data.path("uploadUrl").asText()).isNotBlank();
        assertThat(data.path("expiresInSeconds").asLong()).isPositive();
    }

    @Test
    @DisplayName("png 도 발급된다 — 확장자가 contentType 을 따라간다")
    void presign_supports_png() throws Exception {
        JsonNode data = json(mvc.perform(get("/api/uploads/presign?purpose=PHOTO&contentType=image/png")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())).path("data");

        assertThat(data.path("fileKey").asText()).endsWith(".png");
    }

    @Test
    @DisplayName("purpose 가 목록 밖이면 400 — enum 변환에서 막힌다")
    void presign_rejects_unknown_purpose() throws Exception {
        mvc.perform(get("/api/uploads/presign?purpose=X&contentType=image/jpeg")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("gif 는 400 fields[contentType]")
    void presign_rejects_unsupported_content_type() throws Exception {
        mvc.perform(get("/api/uploads/presign?purpose=PHOTO&contentType=image/gif")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-4000"))
                .andExpect(jsonPath("$.error.fields[0].field").value("contentType"));
    }

    /* ---------------- ② 사진 + 문장 ---------------- */

    @Test
    @DisplayName("사진과 문장을 저장하면 photo 1행 · 생각상자 DIRECT 1행")
    void saving_a_photo_also_writes_the_sentence() throws Exception {
        JsonNode data = json(putPhoto(tokenA, activeSiteId, body(storedPhotoKey(tokenA), SENTENCE, false, false))
                .andExpect(status().isOk())).path("data");

        assertThat(data.path("siteId").asLong()).isEqualTo(activeSiteId);
        assertThat(data.path("siteName").asText()).isNotBlank();
        assertThat(data.path("sentence").asText()).isEqualTo(SENTENCE);

        assertThat(photoRows()).isEqualTo(1);
        assertThat(directRows()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 사찰에 다시 저장하면 행이 늘지 않고 교체된다 — 문장이 바뀌면 is_edited 가 켜진다")
    void saving_again_replaces_instead_of_adding() throws Exception {
        putPhoto(tokenA, activeSiteId, body(storedPhotoKey(tokenA), SENTENCE, false, false)).andExpect(status().isOk());
        String secondKey = storedPhotoKey(tokenA);
        JsonNode data = json(putPhoto(tokenA, activeSiteId, body(secondKey, "다시 와서 다른 각도로 담았다.", false, true))
                .andExpect(status().isOk())).path("data");

        assertThat(photoRows()).isEqualTo(1);
        assertThat(directRows()).isEqualTo(1);
        assertThat(data.path("photoKey").asText()).isEqualTo(secondKey);
        assertThat(data.path("isPrivate").asBoolean()).isTrue();   // 공개 여부도 함께 갈아 끼운다
        assertThat(isEditedOfDirect()).isEqualTo(1);
    }

    @Test
    @DisplayName("남의 키로는 저장할 수 없다 — 400 fields[photoKey]")
    void cannot_save_someone_elses_key() throws Exception {
        long other = userIdA + 1000;
        putPhoto(tokenA, activeSiteId, body("PHOTO/" + other + "/" + UUID.randomUUID() + ".jpg", SENTENCE, false, false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-4000"))
                .andExpect(jsonPath("$.error.fields[0].field").value("photoKey"));

        assertThat(photoRows()).isZero();
    }

    @Test
    @DisplayName("공개되지 않은 사찰에는 남길 수 없다 — 404 SITE-4040")
    void cannot_save_on_a_draft_site() throws Exception {
        putPhoto(tokenA, draftSiteId, body(storedPhotoKey(tokenA), SENTENCE, false, false))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SITE-4040"));
    }

    @Test
    @DisplayName("문장이 301자면 400 — 생각상자와 같은 상한")
    void sentence_over_300_is_rejected() throws Exception {
        putPhoto(tokenA, activeSiteId, body(photoKey(userIdA), "가".repeat(301), false, false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-4000"));
    }

    @Test
    @DisplayName("내 사진 목록과 한 건 조회")
    void list_and_get_my_photos() throws Exception {
        putPhoto(tokenA, activeSiteId, body(storedPhotoKey(tokenA), SENTENCE, false, false)).andExpect(status().isOk());

        JsonNode items = json(mvc.perform(get("/api/photos").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())).path("data").path("items");
        assertThat(items).hasSize(1);

        mvc.perform(get("/api/photos/" + activeSiteId).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sentence").value(SENTENCE));
    }

    /* ---------------- ③ 생각상자 ---------------- */

    @Test
    @DisplayName("자유 기록 작성 · 목록 · 수정 · 삭제")
    void thinkbox_crud() throws Exception {
        long id = createThinkbox("혼자 걷는 길이 생각보다 조용했다.");

        assertThat(json(mvc.perform(get("/api/thinkbox?sort=date")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())).path("data").path("items")).hasSize(1);

        mvc.perform(patch("/api/thinkbox/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"고쳐 적는다. 조용한 것이 아니라 조용해지고 싶었던 것이다."}"""))
                .andExpect(status().isOk());
        assertThat(isEdited(id)).isEqualTo(1);

        mvc.perform(delete("/api/thinkbox/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNoContent());   // 삭제는 204 · 본문 없음(감사 G)
    }

    @Test
    @DisplayName("정렬 값이 목록 밖이면 400 — SQL 에 문자열을 그대로 꽂지 않는다")
    void unknown_sort_is_rejected() throws Exception {
        mvc.perform(get("/api/thinkbox?sort=hack").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-4000"));
    }

    @Test
    @DisplayName("남의 글은 고칠 수도 지울 수도 없다 — 403")
    void cannot_touch_someone_elses_thinkbox() throws Exception {
        long id = createThinkbox("내 글이다.");

        mvc.perform(patch("/api/thinkbox/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"남의 글을 고쳐 본다."}"""))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/thinkbox/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("여섯 달 전 오늘이 없으면 200 + data null — 404 가 아니다")
    void flashback_is_null_when_nothing_was_written() throws Exception {
        mvc.perform(get("/api/thinkbox/flashback").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("여섯 달 전으로 옮겨 두면 그 글이 나온다")
    void flashback_finds_the_entry_from_six_months_ago() throws Exception {
        long id = createThinkbox("여섯 달 전에 쓴 문장이다.");
        jdbc.update("UPDATE thinkbox SET created_at = CURDATE() - INTERVAL 6 MONTH WHERE thinkbox_id = ?", id);

        mvc.perform(get("/api/thinkbox/flashback").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.thinkboxId").value(id))
                .andExpect(jsonPath("$.data.content").value("여섯 달 전에 쓴 문장이다."));
    }

    /* ---------------- ④ 명상 ---------------- */

    @Test
    @DisplayName("명상 목록·상세는 로그인 없이 열리고 공개된 것만 나온다")
    void meditation_is_public_and_active_only() throws Exception {
        JsonNode items = json(mvc.perform(get("/api/meditations")).andExpect(status().isOk()))
                .path("data").path("items");
        assertThat(items).isNotEmpty();

        // 시드에는 DRAFT 명상이 없다. 잠깐 내렸다가 되돌려 "공개된 것만 나온다" 를 실제로 확인한다.
        long id = jdbc.queryForObject(
                "SELECT meditation_id FROM meditation WHERE status = 'ACTIVE' ORDER BY meditation_id DESC LIMIT 1", Long.class);
        jdbc.update("UPDATE meditation SET status = 'DRAFT' WHERE meditation_id = ?", id);
        try {
            mvc.perform(get("/api/meditations/" + id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("MEDITATION-4040"));

            JsonNode after = json(mvc.perform(get("/api/meditations")).andExpect(status().isOk()))
                    .path("data").path("items");
            assertThat(after.size()).as("내린 것은 목록에서도 빠진다").isEqualTo(items.size() - 1);
        } finally {
            jdbc.update("UPDATE meditation SET status = 'ACTIVE' WHERE meditation_id = ?", id);
        }
    }

    @Test
    @DisplayName("번역이 없으면 ko 로 물러나고, 응답의 lang 이 실제로 나간 언어다")
    void meditation_detail_reports_the_language_it_returned() throws Exception {
        long id = jdbc.queryForObject(
                "SELECT meditation_id FROM meditation WHERE status = 'ACTIVE' ORDER BY meditation_id LIMIT 1", Long.class);

        mvc.perform(get("/api/meditations/" + id).header(HttpHeaders.ACCEPT_LANGUAGE, "ja"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lang").value("ko"));   // ja 번역이 없으면 ko 로 물러난다
    }

    @Test
    @DisplayName("재생 기록은 저장된다 · 없는 명상이면 404")
    void meditation_log() throws Exception {
        long id = jdbc.queryForObject(
                "SELECT meditation_id FROM meditation WHERE status = 'ACTIVE' ORDER BY meditation_id LIMIT 1", Long.class);

        mvc.perform(post("/api/meditations/logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"meditationId":%d,"playedSeconds":300,"memo":"앉아서 다섯 번 숨을 세었다."}""".formatted(id)))
                .andExpect(status().is2xxSuccessful());

        mvc.perform(post("/api/meditations/logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"meditationId":999999,"playedSeconds":10}"""))
                .andExpect(status().isNotFound());
    }

    /* ---------------- 내부 ---------------- */

    private static String photoKey(long userId) {
        return "PHOTO/" + userId + "/" + UUID.randomUUID() + ".jpg";
    }

    /**
     * presign 을 받아 <b>바이트까지 실제로 올린 뒤</b> fileKey 를 돌려준다.
     * <p>
     * 11-A STEP 4 부터 저장은 "그 키의 파일이 저장소에 있는가" 를 본다. 그 전에는 키를 지어내도
     * 통과했고, 그래서 <b>사진이 한 장도 저장된 적이 없다는 사실이 시험에도 걸리지 않았다.</b>
     * 이 헬퍼가 프론트가 밟는 길(presign → PUT → 제출)을 그대로 밟는다.
     */
    private String storedPhotoKey(String token) throws Exception {
        JsonNode data = json(mvc.perform(get("/api/uploads/presign?purpose=PHOTO&contentType=image/jpeg")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())).path("data");

        // uploadUrl 은 {endpoint}/{bucket}/{fileKey}?expires=..&signature=.. 다(S3 와 같은 모양).
        // MockMvc 에는 앞의 주소를 떼고 경로부터 넘긴다.
        String uploadUrl = data.path("uploadUrl").asText();
        URI uri = URI.create(uploadUrl.substring(uploadUrl.indexOf("/api/uploads")));
        mvc.perform(put(uri)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.IMAGE_JPEG)
                        .content(JPEG_BYTES))
                .andExpect(status().isCreated());

        return data.path("fileKey").asText();
    }

    private static String body(String key, String sentence, boolean otherFace, boolean isPrivate) {
        return """
                {"photoKey":"%s","sentence":"%s","hasOtherFace":%s,"isPrivate":%s}"""
                .formatted(key, sentence, otherFace, isPrivate);
    }

    private org.springframework.test.web.servlet.ResultActions putPhoto(String token, long siteId, String body) throws Exception {
        return mvc.perform(put("/api/photos/" + siteId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long createThinkbox(String content) throws Exception {
        return json(mvc.perform(post("/api/thinkbox")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"%s"}""".formatted(content)))
                .andExpect(status().isCreated())).path("data").asLong();
    }

    private JsonNode json(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return om.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private Integer photoRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM photo WHERE user_id = ?", Integer.class, userIdA);
    }

    private Integer directRows() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM thinkbox WHERE user_id = ? AND source = 'DIRECT' AND site_id IS NOT NULL",
                Integer.class, userIdA);
    }

    private Integer isEditedOfDirect() {
        return jdbc.queryForObject("""
                SELECT is_edited FROM thinkbox
                 WHERE user_id = ? AND source = 'DIRECT' AND site_id = ?""", Integer.class, userIdA, activeSiteId);
    }

    private Integer isEdited(long thinkboxId) {
        return jdbc.queryForObject("SELECT is_edited FROM thinkbox WHERE thinkbox_id = ?", Integer.class, thinkboxId);
    }

    private void signup(String email, String nickname) throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"%s","nickname":"%s"}""".formatted(email, PASSWORD, nickname)));
    }

    private String login(String email) throws Exception {
        return json(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"%s"}""".formatted(email, PASSWORD)))
                .andExpect(status().isOk())).path("data").path("accessToken").asText();
    }

    private void cleanUp() {
        String in = "(SELECT user_id FROM users WHERE email IN (?, ?))";
        Object[] a = {EMAIL_A, EMAIL_B};
        jdbc.update("DELETE FROM meditation_log WHERE user_id IN " + in, a);
        jdbc.update("DELETE FROM photo WHERE user_id IN " + in, a);
        jdbc.update("DELETE FROM thinkbox WHERE user_id IN " + in, a);
        jdbc.update("DELETE FROM refresh_token WHERE user_id IN " + in, a);
        jdbc.update("DELETE FROM user_agreement WHERE user_id IN " + in, a);
        jdbc.update("DELETE FROM users WHERE email IN (?, ?)", a);
    }
}
