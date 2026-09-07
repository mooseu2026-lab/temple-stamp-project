package com.templestamp.stamp;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 현장 인증 3단계 종단 테스트 — 의견91.md 의 수기 확인 표를 그대로 옮긴 것.
 * <p>
 * <b>이 테스트는 교재(ch5.md [기본 44]~[기본 48])가 정본이다.</b> 저장소가 아직 교재와 다른 부분은
 * 여기서 실패하고, 그 실패 목록이 곧 STEP 2 에서 고칠 것이다. 통과하도록 테스트를 고치지 않는다.
 * <p>
 * 수기로 확인하던 것을 테스트로 옮기는 이유는 하나다. 3단계는 <b>상태 전이</b>라서 한 단계만 봐서는
 * 아무것도 검증되지 않는다. 순서 위반·만료·이동시간은 전부 "앞 단계가 언제 어떤 상태였는가" 에 달려 있고,
 * 그건 컨트롤러부터 DB 까지 실제로 지나가 봐야 안다.
 * <p>
 * 만료(60분)는 기다릴 수 없으므로 {@code gps_verified_at} 을 61분 전으로 UPDATE 한 뒤 QR 을 친다.
 * 그때 EXPIRED 가 DB 에 남아 있는지가 REQUIRES_NEW(별도 트랜잭션) 가 실제로 동작하는지의 유일한 증거다 —
 * 같은 트랜잭션이었다면 뒤따르는 409 예외가 그 기록까지 되돌린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StampFlowIntegrationTest {

    private static final long COURSE_ID = 1L;
    private static final String EMAIL_A = "stamp-it-a@test.com";
    private static final String EMAIL_B = "stamp-it-b@test.com";
    private static final String PASSWORD = "Test1234!";
    private static final String ADMIN_EMAIL = "admin@templestamp.local";
    private static final String ADMIN_PASSWORD = "Admin1234!";
    private static final String SENTENCE = "오늘 받은 것들의 이름을 하나씩 불러본다.";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;

    private String tokenA;
    private String tokenB;
    private String adminToken;
    private long userIdA;

    /** course_site_id — 자리 1~5. slot[i] 의 대표 사찰이 site[i]. */
    private long[] slot;
    private long[] site;

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
        adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        signup(EMAIL_A, "순례자갑");
        signup(EMAIL_B, "순례자을");
        tokenA = login(EMAIL_A, PASSWORD);
        tokenB = login(EMAIL_B, PASSWORD);
        agreeLocation(tokenA);
        agreeLocation(tokenB);
        userIdA = jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, EMAIL_A);

        slot = new long[6];
        site = new long[6];
        List<java.util.Map<String, Object>> rows = jdbc.queryForList(
                "SELECT course_site_id, site_id, position FROM course_site WHERE course_id = ? ORDER BY position", COURSE_ID);
        for (var row : rows) {
            int p = ((Number) row.get("position")).intValue();
            slot[p] = ((Number) row.get("course_site_id")).longValue();
            site[p] = ((Number) row.get("site_id")).longValue();
        }

        // v4: 각 자리의 대표를 후보(slot_site)로 등록한다. 교재의 gpsCheck 는 후보가 아니면 거절한다.
        for (int p = 1; p <= 5; p++) {
            jdbc.update("""
                    INSERT IGNORE INTO slot_site (course_site_id, site_id, track, sort_no)
                    VALUES (?, ?, 'MAIN', 1)""", slot[p], site[p]);
        }
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    /* ---------------- ① 정상 흐름 ---------------- */

    @Test
    @DisplayName("T-01 GPS → QR → 미션 = COMPLETED, 진행률 1/5, 보상 0건(챕터 11 — 도장 보상은 껐다)")
    void full_three_steps_complete_the_stamp() throws Exception {
        long stampId = gpsOk(1);
        qrOk(stampId, 1);

        JsonNode result = mission(stampId, SENTENCE).get("data");
        assertThat(result.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(result.path("progress").path("completedCount").asInt()).isEqualTo(1);
        // 챕터 11 결정 A — 도장마다 주던 보상 정책은 is_active=0 이다. 필드는 남고 목록만 빈다.
        assertThat(result.path("rewards")).isEmpty();
        assertThat(result.path("courseCompleted").asBoolean()).isFalse();

        assertThat(dbStatus(stampId)).isEqualTo("COMPLETED");
        // 완료 뒤에만 남는 기록들 — 미리보기에서는 남지 않아야 하는 것들이다
        assertThat(countThinkboxFromMission(stampId)).isEqualTo(1);
    }

    /* ---------------- ② 1단계에서 막히는 것 ---------------- */

    @Test
    @DisplayName("T-03 정확도 LOW 는 저장조차 되지 않는다 — 400")
    void low_accuracy_is_rejected_before_insert() throws Exception {
        mvc.perform(userPost(tokenA, "/api/stamps/" + slot[1] + "/gps-check", gpsBody(site[1], true, "LOW")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("STAMP-4001"));

        assertThat(countStamps()).isZero();
    }

    @Test
    @DisplayName("T-04 반경 밖 — 400")
    void outside_radius_is_rejected() throws Exception {
        mvc.perform(userPost(tokenA, "/api/stamps/" + slot[1] + "/gps-check", gpsBody(site[1], false, "HIGH")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("STAMP-4000"));

        assertThat(countStamps()).isZero();
    }

    @Test
    @DisplayName("T-04b 그 자리의 후보가 아닌 사찰이면 400 COURSE-4001 (v4)")
    void site_that_is_not_a_candidate_is_rejected() throws Exception {
        mvc.perform(userPost(tokenA, "/api/stamps/" + slot[1] + "/gps-check", gpsBody(site[2], true, "HIGH")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COURSE-4001"));
    }

    /* ---------------- ③ 2단계 — QR ---------------- */

    @Test
    @DisplayName("T-05 다른 사찰의 QR 은 400")
    void qr_of_another_site_is_rejected() throws Exception {
        long stampId = gpsOk(1);
        String otherQr = issueQr(site[2]);

        mvc.perform(userPost(tokenA, "/api/stamps/" + stampId + "/qr", qrBody(otherQr)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("STAMP-4004"));
    }

    @Test
    @DisplayName("T-06 회전(qr_version+1) 뒤의 옛 QR 은 400")
    void rotated_qr_version_is_rejected() throws Exception {
        String oldQr = issueQr(site[1]);
        mvc.perform(adminPost("/api/admin/sites/" + site[1] + "/qr/rotate", "{}"))
                .andExpect(status().isOk());

        long stampId = gpsOk(1);
        mvc.perform(userPost(tokenA, "/api/stamps/" + stampId + "/qr", qrBody(oldQr)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("T-02 QR 을 건너뛰고 미션을 내면 409 순서 위반")
    void mission_without_qr_is_out_of_order() throws Exception {
        long stampId = gpsOk(1);

        mvc.perform(userPost(tokenA, "/api/stamps/" + stampId + "/mission", missionBody(SENTENCE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STAMP-4092"));

        assertThat(dbStatus(stampId)).isEqualTo("GPS_DONE");   // 실패한 요청이 상태를 바꾸지 않는다
    }

    @Test
    @DisplayName("T-08 60분이 지나면 409 이고, DB 에 EXPIRED 가 남는다 (REQUIRES_NEW 실측)")
    void expired_session_is_recorded_before_the_exception() throws Exception {
        long stampId = gpsOk(1);
        String qr = issueQr(site[1]);
        jdbc.update("UPDATE stamp SET gps_verified_at = NOW() - INTERVAL 61 MINUTE WHERE stamp_id = ?", stampId);

        mvc.perform(userPost(tokenA, "/api/stamps/" + stampId + "/qr", qrBody(qr)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STAMP-4091"));

        // ★ 여기가 핵심이다. 409 예외가 자기 트랜잭션을 롤백하는데도 이 기록은 남아 있어야 한다.
        assertThat(dbStatus(stampId)).isEqualTo("EXPIRED");
    }

    /* ---------------- ④ 3단계 — 미션·이동시간 ---------------- */

    @Test
    @DisplayName("T-12 직전 도장에서 최소 이동시간을 못 채우면 PENDING(TRAVEL_TIME), 보상 없음")
    void too_fast_between_sites_goes_pending() throws Exception {
        complete(1);   // 자리 1 을 방금 끝냈다

        long stampId = gpsOk(2);
        qrOk(stampId, 2);
        JsonNode result = mission(stampId, SENTENCE).get("data");

        assertThat(result.path("status").asText()).isEqualTo("PENDING");
        assertThat(result.path("rewards")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT pending_reason FROM stamp WHERE stamp_id = ?", String.class, stampId))
                .isEqualTo("TRAVEL_TIME");
    }

    @Test
    @DisplayName("T-11 같은 자리에 두 번째 도장은 409 이고, 메시지에 사찰 이름과 발행일이 있다")
    void second_stamp_on_the_same_slot_is_rejected_with_detail() throws Exception {
        complete(1);
        String siteName = jdbc.queryForObject("SELECT name FROM site WHERE site_id = ?", String.class, site[1]);

        mvc.perform(userPost(tokenA, "/api/stamps/" + slot[1] + "/gps-check", gpsBody(site[1], true, "HIGH")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STAMP-4090"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString(siteName)));
    }

    @Test
    @DisplayName("T-11b 이미 발행된 자리의 도장을 by-slot 으로 곧바로 볼 수 있다 (Q1 ①)")
    void by_slot_returns_the_issued_stamp() throws Exception {
        complete(1);

        JsonNode data = om.readTree(mvc.perform(get("/api/stamps/by-slot/" + slot[1])
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data");

        assertThat(data.path("siteId").asLong()).isEqualTo(site[1]);
        assertThat(data.path("siteName").asText()).isNotBlank();
        assertThat(data.path("userSentence").asText()).isEqualTo(SENTENCE);
        assertThat(data.path("status").asText()).isEqualTo("COMPLETED");
    }

    /* ---------------- ⑤ 한도·권한 ---------------- */

    @Test
    @DisplayName("T-09 하루 5개를 채우면 여섯 번째 도착 확인은 429")
    void sixth_stamp_of_the_day_is_throttled() throws Exception {
        long pilgrimageId = startPilgrimage();
        for (int p = 1; p <= 5; p++) {
            insertStamp(pilgrimageId, p, "COMPLETED");
        }

        mvc.perform(userPost(tokenA, "/api/stamps/" + slot[1] + "/gps-check", gpsBody(site[1], true, "HIGH")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("T-09b 보류(PENDING)와 진행 중도 한도에 들어간다 — PENDING 4 + 진행 중 1 이면 여섯 번째는 429")
    void pending_and_in_progress_count_toward_the_daily_limit() throws Exception {
        // COMPLETED 만 세면 여기가 0 이 되어, 보류로 빠져나간 뒤 승인받는 방식으로 한도를 넘길 수 있다.
        long pilgrimageId = startPilgrimage();
        for (int p = 1; p <= 4; p++) {
            insertStamp(pilgrimageId, p, "PENDING");
        }
        insertStamp(pilgrimageId, 5, "GPS_DONE");

        mvc.perform(userPost(tokenA, "/api/stamps/" + slot[1] + "/gps-check", gpsBody(site[1], true, "HIGH")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("STAMP-4291"));
    }

    @Test
    @DisplayName("T-09c 만료·반려는 한도를 깎지 않는다 — 실패한 시도라 다시 할 수 있어야 한다")
    void expired_and_rejected_do_not_count() throws Exception {
        long pilgrimageId = startPilgrimage();
        for (int p = 1; p <= 3; p++) {
            insertStamp(pilgrimageId, p, "EXPIRED");
        }
        insertStamp(pilgrimageId, 4, "REJECTED");
        insertStamp(pilgrimageId, 5, "COMPLETED");

        mvc.perform(userPost(tokenA, "/api/stamps/" + slot[1] + "/gps-check", gpsBody(site[1], true, "HIGH")))
                .andExpect(status().isOk());   // 살아 있는 것은 1건뿐이다
    }

    @Test
    @DisplayName("T-13 남의 스탬프는 403 — 404 가 아니다(존재 여부를 숨기지 않는다는 뜻이 아니라 명세)")
    void other_users_stamp_is_forbidden() throws Exception {
        long stampId = gpsOk(1);

        mvc.perform(get("/api/stamps/" + stampId).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T-16 예외 접수는 하루 2건까지 — 세 번째는 429")
    void third_evidence_of_the_day_is_throttled() throws Exception {
        for (int p = 1; p <= 2; p++) {
            mvc.perform(userPost(tokenA, "/api/stamps/evidence", evidenceBody(p)))
                    .andExpect(status().isOk());
        }
        mvc.perform(userPost(tokenA, "/api/stamps/evidence", evidenceBody(3)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("T-16b 증빙 접수도 그 자리의 후보 사찰이어야 한다 — 400 COURSE-4001 (v4)")
    void evidence_site_must_be_a_candidate() throws Exception {
        String body = """
                {"courseSiteId":%d,"siteId":%d,"sentence":"현장에서 QR 이 훼손되어 사진으로 대신합니다.",
                 "photoKey":"EVIDENCE/%d/%s.jpg"}"""
                .formatted(slot[1], site[2], userIdA, java.util.UUID.randomUUID());

        mvc.perform(userPost(tokenA, "/api/stamps/evidence", body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COURSE-4001"));
    }

    @Test
    @DisplayName("T-16c 증빙으로 접수한 도장에도 site_id 가 남는다 — 승인 뒤 이동시간·여권 사찰명이 맞으려면")
    void evidence_records_the_site() throws Exception {
        mvc.perform(userPost(tokenA, "/api/stamps/evidence", evidenceBody(1)))
                .andExpect(status().isOk());

        Long stored = jdbc.queryForObject("""
                SELECT st.site_id FROM stamp st JOIN pilgrimage p ON p.pilgrimage_id = st.pilgrimage_id
                 WHERE p.user_id = ? AND st.course_site_id = ?""", Long.class, userIdA, slot[1]);
        assertThat(stored).isEqualTo(site[1]);
    }

    @Test
    @DisplayName("A-2 경로 변수의 @Positive 가 실제로 걸린다 — 400 COMMON-4000 (전에는 404 였다)")
    void non_positive_path_variable_is_a_validation_error() throws Exception {
        // @Validated 가 클래스에 없으면 이 제약이 예외도 경고도 없이 통과해 404 STAMP-4040 이 나갔다.
        mvc.perform(get("/api/stamps/-5").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-4000"));

        mvc.perform(get("/api/stamps/by-slot/0").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-4000"));
    }

    @Test
    @DisplayName("B-3 어제 만료된 행을 오늘 다시 쓰면 오늘 한도에 잡힌다")
    void reused_stale_stamp_counts_toward_today() throws Exception {
        long pilgrimageId = startPilgrimage();
        // 어제 GPS 만 찍고 만료된 행. created_at 이 어제라 그대로 두면 오늘 한도에서 빠진다.
        jdbc.update("""
                INSERT INTO stamp (pilgrimage_id, course_site_id, site_id, verify_status, verify_method,
                                   gps_verified_at, created_at)
                VALUES (?, ?, ?, 'EXPIRED', 'GPS_QR', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY)""",
                pilgrimageId, slot[1], site[1]);
        assertThat(activeToday()).isZero();

        mvc.perform(userPost(tokenA, "/api/stamps/" + slot[1] + "/gps-check", gpsBody(site[1], true, "HIGH")))
                .andExpect(status().isOk());

        assertThat(activeToday()).as("재사용한 행이 오늘 한도에 잡힌다").isEqualTo(1);
        assertThat(countStamps()).as("행이 늘지 않았다 — 같은 행을 다시 쓴 것이다").isEqualTo(1);
    }

    @Test
    @DisplayName("B-5 증빙 접수는 EXPIRED 를 거치지 않고 처음부터 PENDING 으로 들어간다")
    void evidence_is_inserted_as_pending_from_the_start() throws Exception {
        mvc.perform(userPost(tokenA, "/api/stamps/evidence", evidenceBody(1)))
                .andExpect(status().isOk());

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT st.verify_status, st.verify_method, st.pending_reason, st.evidence_photo_key, st.site_id
                  FROM stamp st JOIN pilgrimage p ON p.pilgrimage_id = st.pilgrimage_id
                 WHERE p.user_id = ? AND st.course_site_id = ?""", userIdA, slot[1]);

        assertThat(row.get("verify_status")).isEqualTo("PENDING");
        assertThat(row.get("verify_method")).isEqualTo("EVIDENCE");
        assertThat(row.get("pending_reason")).isEqualTo("EVIDENCE");
        assertThat(row.get("evidence_photo_key")).asString().startsWith("EVIDENCE/");
        assertThat(((Number) row.get("site_id")).longValue()).isEqualTo(site[1]);
    }

    /* ---------------- 흐름 도우미 ---------------- */

    /** 오늘 만든 도장 중 EXPIRED·REJECTED 를 뺀 수 — 하루 한도가 세는 것과 같은 기준. */
    private Integer activeToday() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM stamp st JOIN pilgrimage p ON p.pilgrimage_id = st.pilgrimage_id
                 WHERE p.user_id = ? AND st.created_at >= CURDATE()
                   AND st.verify_status NOT IN ('EXPIRED', 'REJECTED')""", Integer.class, userIdA);
    }

    /** 한도 계산을 보기 위한 직접 삽입. 상태별로 세는 규칙이 달라 API 로는 만들 수 없는 조합이다. */
    private void insertStamp(long pilgrimageId, int position, String status) {
        jdbc.update("""
                INSERT INTO stamp (pilgrimage_id, course_site_id, site_id, verify_status, verify_method,
                                   gps_verified_at, qr_verified_at, mission_verified_at, user_sentence)
                VALUES (?, ?, ?, ?, 'GPS_QR', NOW(), NOW(),
                        CASE WHEN ? = 'COMPLETED' THEN NOW() ELSE NULL END, ?)""",
                pilgrimageId, slot[position], site[position], status, status, SENTENCE);
    }

    private long gpsOk(int position) throws Exception {
        String body = mvc.perform(userPost(tokenA, "/api/stamps/" + slot[position] + "/gps-check",
                        gpsBody(site[position], true, "HIGH")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body).path("data").path("stampId").asLong();
    }

    private void qrOk(long stampId, int position) throws Exception {
        mvc.perform(userPost(tokenA, "/api/stamps/" + stampId + "/qr", qrBody(issueQr(site[position]))))
                .andExpect(status().isOk());
    }

    private JsonNode mission(long stampId, String sentence) throws Exception {
        String body = mvc.perform(userPost(tokenA, "/api/stamps/" + stampId + "/mission", missionBody(sentence)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body);
    }

    private void complete(int position) throws Exception {
        long stampId = gpsOk(position);
        qrOk(stampId, position);
        mission(stampId, SENTENCE);
    }

    private String issueQr(long siteId) throws Exception {
        String body = mvc.perform(adminPost("/api/admin/sites/" + siteId + "/qr", "{}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body).path("data").path("qrToken").asText();
    }

    private long startPilgrimage() throws Exception {
        mvc.perform(userPost(tokenA, "/api/pilgrimages", """
                {"courseId":%d}""".formatted(COURSE_ID))).andExpect(status().is2xxSuccessful());
        return jdbc.queryForObject("SELECT pilgrimage_id FROM pilgrimage WHERE user_id = ? AND course_id = ?",
                Long.class, userIdA, COURSE_ID);
    }

    /* ---------------- 본문 ---------------- */

    private static String gpsBody(long siteId, boolean withinRadius, String grade) {
        return """
                {"siteId":%d,"withinRadius":%s,"accuracyGrade":"%s"}""".formatted(siteId, withinRadius, grade);
    }

    private static String qrBody(String token) {
        return """
                {"qrToken":"%s"}""".formatted(token);
    }

    private static String missionBody(String sentence) {
        return """
                {"sentence":"%s"}""".formatted(sentence);
    }

    private String evidenceBody(int position) {
        return """
                {"courseSiteId":%d,"siteId":%d,"sentence":"%s","photoKey":"EVIDENCE/%d/%s.jpg"}"""
                .formatted(slot[position], site[position], "현장에서 QR 이 훼손되어 사진으로 대신합니다.",
                        userIdA, java.util.UUID.randomUUID());
    }

    /* ---------------- 조회·정리 ---------------- */

    private String dbStatus(long stampId) {
        return jdbc.queryForObject("SELECT verify_status FROM stamp WHERE stamp_id = ?", String.class, stampId);
    }

    private Integer countStamps() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM stamp st JOIN pilgrimage p ON p.pilgrimage_id = st.pilgrimage_id
                 WHERE p.user_id = ?""", Integer.class, userIdA);
    }

    private Integer countThinkboxFromMission(long stampId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM thinkbox WHERE stamp_id = ? AND source = 'MISSION'", Integer.class, stampId);
    }

    private MockHttpServletRequestBuilder userPost(String token, String url, String body) {
        return post(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder adminPost(String url, String body) {
        return post(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }


    /** GPS 인증은 위치기반서비스 동의가 선행 조건이다(USER-4031). 동의 없이는 1단계에서 403 이라 아무것도 못 본다. */
    private void agreeLocation(String token) throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/users/me/agreements")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        [{"agreementType":"LOCATION_SERVICE","version":1}]"""));
    }

    private void signup(String email, String nickname) throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"%s","nickname":"%s"}""".formatted(email, PASSWORD, nickname)))
                .andExpect(status().is2xxSuccessful());
    }

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"%s"}""".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body).path("data").path("accessToken").asText();
    }

    /** FK 순서: user_reward → thinkbox → stamp → pilgrimage → seen → refresh_token → users. */
    private void cleanUp() {
        String emails = "(?, ?)";
        Object[] args = {EMAIL_A, EMAIL_B};
        // 배송 정보가 user_reward 를 RESTRICT 로 잡는다 — 먼저 지운다(챕터 7 보강 B-4).
        jdbc.update("DELETE FROM reward_claim WHERE user_reward_id IN (SELECT user_reward_id FROM user_reward WHERE user_id IN (SELECT user_id FROM users WHERE email IN " + emails + "))", args);
        jdbc.update("DELETE FROM user_reward WHERE user_id IN (SELECT user_id FROM users WHERE email IN " + emails + ")", args);
        jdbc.update("DELETE FROM thinkbox WHERE user_id IN (SELECT user_id FROM users WHERE email IN " + emails + ")", args);
        jdbc.update("""
                DELETE FROM stamp WHERE pilgrimage_id IN
                  (SELECT pilgrimage_id FROM pilgrimage WHERE user_id IN
                    (SELECT user_id FROM users WHERE email IN """ + emails + "))", args);
        jdbc.update("DELETE FROM pilgrimage WHERE user_id IN (SELECT user_id FROM users WHERE email IN " + emails + ")", args);
        jdbc.update("DELETE FROM phrase_seen WHERE user_id IN (SELECT user_id FROM users WHERE email IN " + emails + ")", args);
        jdbc.update("DELETE FROM task_seen WHERE user_id IN (SELECT user_id FROM users WHERE email IN " + emails + ")", args);
        jdbc.update("DELETE FROM refresh_token WHERE user_id IN (SELECT user_id FROM users WHERE email IN " + emails + ")", args);
        jdbc.update("DELETE FROM user_agreement WHERE user_id IN (SELECT user_id FROM users WHERE email IN " + emails + ")", args);
        jdbc.update("DELETE FROM users WHERE email IN " + emails, args);
        // slot_site 는 data.sql 이 넣는 데모 행이라 지우지 않는다 — 지우면 다음 실행이 COURSE-4001 로 막힌다.
        jdbc.update("UPDATE site SET qr_version = 1 WHERE site_id <= 5");
    }
}
