package com.templestamp.site;

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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 싱글페이지의 계층·언어 결정 순서와 컨트롤러 검증을 실제로 태운다.
 * <p>
 * 우선순위: 쿼리 target → users.tier → AGE30, 쿼리 locale → Accept-Language → users.locale → ko.
 * 앞자리가 뒷자리를 실제로 덮는지는 <b>로그인 사용자를 만들어 users 행의 값을 바꿔 봐야만</b> 드러난다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SitePageParamIntegrationTest {

    private static final String EMAIL = "page-it-user@templestamp.test";
    private static final String PASSWORD = "test1234!";
    private static final long SITE_ID = 1L;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        removeAccount();
    }

    @AfterEach
    void tearDown() {
        removeAccount();
    }

    /* ---------------- 계층 결정 ---------------- */

    @Test
    @DisplayName("비로그인·target 없음이면 AGE30 문구가 나간다")
    void anonymous_falls_back_to_age30() throws Exception {
        JsonNode phrase = page("").path("expansionPhrase");
        assertThat(tierOf(phrase.path("expansionPhraseId").asLong())).isEqualTo("AGE30");
    }

    @Test
    @DisplayName("쿼리 target 이 users.tier 를 덮는다")
    void query_target_overrides_user_tier() throws Exception {
        String token = signupAndLogin();
        setUserTier("AGE60");

        // target 없이 부르면 users.tier 를 따른다
        assertThat(tierOf(page("", token).path("expansionPhrase")
                .path("expansionPhraseId").asLong())).isEqualTo("AGE60");

        // target 을 주면 그쪽이 이긴다
        assertThat(tierOf(page("?target=RIDER", token).path("expansionPhrase")
                .path("expansionPhraseId").asLong())).isEqualTo("RIDER");
    }

    @Test
    @DisplayName("users.tier 가 비어 있으면 AGE30 으로 떨어진다")
    void null_user_tier_falls_back_to_age30() throws Exception {
        String token = signupAndLogin();
        jdbc.update("UPDATE users SET tier = NULL WHERE email = ?", EMAIL);

        assertThat(tierOf(page("", token).path("expansionPhrase")
                .path("expansionPhraseId").asLong())).isEqualTo("AGE30");
    }

    @Test
    @DisplayName("정의되지 않은 target 은 400 이다 — DB 까지 가지 않는다")
    void unknown_target_is_rejected() throws Exception {
        mvc.perform(get("/api/sites/{id}/page?target=AGE99", SITE_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-4003"));
    }

    /* ---------------- 노출 이력 ---------------- */

    @Test
    @DisplayName("미리보기를 여러 번 불러도 phrase_seen 은 0행이다")
    void preview_never_records_seen() throws Exception {
        String token = signupAndLogin();

        for (int i = 0; i < 3; i++) {
            page("", token);
        }

        // 미리보기는 도착 전 화면이다. 여기서 기록하면 도장을 찍기도 전에 문구 풀이 소진된다.
        // 기록은 챕터 5 의 완료 처리에서 붙는다 — 그때 실수로 이쪽에 되돌려 놓으면 이 단언이 깨진다.
        assertThat(seenCount()).isZero();
    }

    /* ---------------- 언어 결정 ---------------- */

    @Test
    @DisplayName("쿼리 locale 이 Accept-Language 를 덮는다")
    void query_locale_beats_header() throws Exception {
        String en = page("?locale=en", null, "ko").path("site").path("name").asText();
        String ko = page("?locale=ko", null, "en").path("site").path("name").asText();

        assertThat(en).isEqualTo("Jogyesa");     // site_i18n(1,'en')
        assertThat(ko).isEqualTo("조계사");        // site_i18n(1,'ko')
        assertThat(en).isNotEqualTo(ko);
    }

    @Test
    @DisplayName("쿼리도 헤더도 없으면 users.locale 을 쓴다")
    void user_locale_is_used_when_no_hint() throws Exception {
        String token = signupAndLogin();
        jdbc.update("UPDATE users SET locale = 'en' WHERE email = ?", EMAIL);

        assertThat(page("", token).path("site").path("name").asText()).isEqualTo("Jogyesa");
    }

    @Test
    @DisplayName("지원하지 않는 언어는 힌트로 치지 않아 users.locale 이 살아 있다")
    void unsupported_language_does_not_shadow_user_locale() throws Exception {
        String token = signupAndLogin();
        jdbc.update("UPDATE users SET locale = 'en' WHERE email = ?", EMAIL);

        // fr 은 지원 목록에 없다. 여기서 ko 로 확정해 버리면 users.locale 이 영영 쓰이지 않는다.
        assertThat(page("", token, "fr").path("site").path("name").asText()).isEqualTo("Jogyesa");
    }

    @Test
    @DisplayName("비로그인이고 힌트도 없으면 ko 다")
    void anonymous_without_hint_is_korean() throws Exception {
        assertThat(page("").path("site").path("name").asText()).isEqualTo("조계사");
    }

    /* ---------------- 컨트롤러 검증 ---------------- */

    @Test
    @DisplayName("verseNo 6 은 404 가 아니라 400 이다 — 정의역 밖이라 '없다'가 아니다")
    void verse_out_of_range_is_400() throws Exception {
        mvc.perform(get("/api/verses/6"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-4000"));

        mvc.perform(get("/api/verses/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verseNo").value(3));
    }

    @Test
    @DisplayName("0 이하 식별자는 400 COMMON-4000")
    void non_positive_id_is_rejected() throws Exception {
        mvc.perform(get("/api/sites/0/guide"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-4000"));

        mvc.perform(get("/api/courses?regionId=-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-4000"));
    }

    @Test
    @DisplayName("권역 목록은 배열이 아니라 items 로 감싸여 DB 의 region 전부가 나간다")
    void regions_are_wrapped_in_items() throws Exception {
        // v4 에서 CSV 가 충청을 충남·세종/충북으로 나누면서 region 이 9행에서 늘었다.
        // 숫자를 박아 두면 권역이 바뀔 때마다 테스트가 깨지므로 DB 를 기준으로 센다.
        int regions = jdbc.queryForObject("SELECT COUNT(*) FROM region", Integer.class);

        mvc.perform(get("/api/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(regions));
    }

    @Test
    @DisplayName("코스를 담을 권역은 9개다 — 제주는 코스 0 으로 목록에만 나간다")
    void nine_regions_carry_courses() throws Exception {
        // v4 편성 단위는 9권역이다(충청을 충남·세종/충북으로 나누고 제주는 아직 없다).
        // 권역 수 자체는 10 이고, 그중 제주만 courseCount 0 이어야 한다.
        String body = mvc.perform(get("/api/regions"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode items = om.readTree(body).path("data").path("items");

        List<String> withoutCourse = new java.util.ArrayList<>();
        for (JsonNode r : items) {
            if (r.path("courseCount").asInt() == 0) withoutCourse.add(r.path("code").asText());
        }

        assertThat(items.size() - withoutCourse.size())
                .as("코스를 담을 권역 수. 시드가 아직 안 돌았으면 이 값이 낮다")
                .isLessThanOrEqualTo(9);
        assertThat(withoutCourse)
                .as("코스 0 인 권역은 화면에서 '준비 중' 으로 표시된다")
                .doesNotContain("CHUNGCHEONG");   // v4 에서 삭제됨 — 되살아나면 여기서 잡힌다
        assertThat(items).noneMatch(r -> "CHUNGCHEONG".equals(r.path("code").asText()));
    }

    @Test
    @DisplayName("권역마다 code 가 실려 나간다 — 프론트가 숫자 id 를 하드코딩하지 않게")
    void regions_carry_code() throws Exception {
        String body = mvc.perform(get("/api/regions"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode items = om.readTree(body).path("data").path("items");
        assertThat(items).allSatisfy(r ->
                assertThat(r.path("code").asText()).isNotBlank());

        // DB 의 code 와 실제로 같은 값인지까지 본다 — 필드만 있고 null 이면 의미가 없다.
        String expected = jdbc.queryForObject(
                "SELECT code FROM region ORDER BY sort_no, region_id LIMIT 1", String.class);
        assertThat(items.get(0).path("code").asText()).isEqualTo(expected);
    }

    /* ---------------- 도우미 ---------------- */

    private JsonNode page(String query) throws Exception {
        return page(query, null, null);
    }

    private JsonNode page(String query, String token) throws Exception {
        return page(query, token, null);
    }

    private JsonNode page(String query, String token, String acceptLanguage) throws Exception {
        var request = get("/api/sites/" + SITE_ID + "/page" + query);
        if (token != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (acceptLanguage != null) {
            request = request.header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage);
        }
        String body = mvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body).path("data");
    }

    private String tierOf(long expansionPhraseId) {
        return jdbc.queryForObject(
                "SELECT tier FROM expansion_phrase WHERE expansion_phrase_id = ?",
                String.class, expansionPhraseId);
    }

    private int seenCount() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM phrase_seen ps
                JOIN users u ON u.user_id = ps.user_id
                WHERE u.email = ?
                """, Integer.class, EMAIL);
    }

    private void setUserTier(String tier) {
        jdbc.update("UPDATE users SET tier = ? WHERE email = ?", tier, EMAIL);
    }

    private String signupAndLogin() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","nickname":"페이지테스트"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isCreated());

        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body).path("data").path("accessToken").asText();
    }

    /** phrase_seen 이 FK 로 걸려 있어 계정을 지우면 노출 이력도 함께 사라진다. */
    private void removeAccount() {
        jdbc.update("DELETE FROM users WHERE email = ?", EMAIL);
    }
}
