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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 완주 연쇄의 하한과 멱등 — 정밀 점검 A-1·B-2.
 * <p>
 * <b>왜 필요한가</b> — "완주한 코스 수 ≥ 전체 ACTIVE 코스 수" 는 코스가 많을 때는 옳지만
 * <b>코스가 적을 때 너무 쉽게 참이 된다.</b> ACTIVE 코스가 1개뿐인 초기에는 코스 하나를 끝낸 사람에게
 * 회향 인증서와 <b>실물 기념품 신청권</b>이 나갔다. 되돌리기 어려운 종류의 사고라 하한을 둔다.
 * <p>
 * 코스를 실제로 12개 ACTIVE 로 만들 수는 없으므로(사찰 110곳의 QR 힌트가 없다),
 * 시드 코스를 잠시 ACTIVE 로 올렸다가 되돌리는 방식으로 "코스가 많은 상태" 를 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CompletionHardeningIntegrationTest {

    private static final long COURSE_ID = 1L;
    private static final String EMAIL = "hardening@test.com";
    private static final String PASSWORD = "Test1234!";
    private static final String SENTENCE = "오늘 받은 것들의 이름을 하나씩 불러본다.";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;

    private String token;
    private String adminToken;
    private long userId;
    private long pilgrimageId;
    private long[] slot = new long[6];
    private long[] site = new long[6];

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
        adminToken = login("admin@templestamp.local", "Admin1234!");
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"%s","nickname":"하한검사"}""".formatted(EMAIL, PASSWORD)));
        token = login(EMAIL, PASSWORD);
        mvc.perform(post("/api/users/me/agreements")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        [{"agreementType":"LOCATION_SERVICE","version":1}]"""));
        userId = jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, EMAIL);

        for (var row : jdbc.queryForList(
                "SELECT course_site_id, site_id, position FROM course_site WHERE course_id = ? ORDER BY position", COURSE_ID)) {
            int p = ((Number) row.get("position")).intValue();
            slot[p] = ((Number) row.get("course_site_id")).longValue();
            site[p] = ((Number) row.get("site_id")).longValue();
        }

        mvc.perform(post("/api/pilgrimages").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"courseId":%d}""".formatted(COURSE_ID)));
        pilgrimageId = jdbc.queryForObject(
                "SELECT pilgrimage_id FROM pilgrimage WHERE user_id = ? AND course_id = ?", Long.class, userId, COURSE_ID);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("A-1 ACTIVE 코스가 하한(12)에 못 미치면 코스를 다 끝내도 회향은 발급되지 않는다")
    void hoehyang_is_withheld_until_enough_courses_are_open() throws Exception {
        assertThat(activeCourses()).as("이 테스트의 전제 — 지금 열린 코스는 몇 개 안 된다").isLessThan(12);

        completeWholeCourse();

        assertThat(pilgrimageStatus()).isEqualTo("COMPLETED");
        assertThat(certCount("PILGRIMAGE")).as("코스 완주 인증서는 나온다").isEqualTo(1);

        assertThat(certCount("HOEHYANG")).as("회향 인증서").isZero();
        assertThat(ebookCount("HOEHYANG")).as("회향본 대기열").isZero();
        assertThat(rewardCount("ON_ALL_COMPLETED")).as("회향 보상").isZero();
    }

    @Test
    @DisplayName("A-1 코스가 하한만큼 열려 있고 그것을 전부 끝내면 회향이 발급된다")
    void hoehyang_is_issued_once_enough_courses_are_open_and_all_done() throws Exception {
        // 시드 코스를 잠시 열어 "코스가 많은 상태" 를 만든다. tearDown 이 되돌린다.
        openSeedCourses();
        assertThat(activeCourses()).isGreaterThanOrEqualTo(12);

        // 열린 코스를 전부 완주한 것으로 만든다 — 코스 1 만 실제 도장을 찍고 나머지는 완주 상태로 둔다.
        completeEveryOtherActiveCourse();
        completeWholeCourse();

        assertThat(certCount("HOEHYANG")).as("회향 인증서").isEqualTo(1);
        assertThat(ebookCount("HOEHYANG")).as("회향본 대기열").isEqualTo(1);
    }

    @Test
    @DisplayName("B-2 반려 → 재승인을 거쳐도 전자책 대기열 행 수는 변하지 않는다")
    void requeue_after_reject_does_not_duplicate_ebooks() throws Exception {
        openSeedCourses();
        completeEveryOtherActiveCourse();
        completeWholeCourse();

        int before = ebookCount(null);
        assertThat(before).as("코스본 + 회향본").isGreaterThanOrEqualTo(2);

        // 마지막 도장을 보류로 되돌렸다가 관리자가 다시 승인한다 — 완주 연쇄를 한 번 더 태운다.
        long last = jdbc.queryForObject("""
                SELECT stamp_id FROM stamp WHERE pilgrimage_id = ? ORDER BY stamp_id DESC LIMIT 1""",
                Long.class, pilgrimageId);
        mvc.perform(adminPost("/api/admin/stamps/" + last + "/review", """
                {"decision":"REJECT","reason":"확인이 필요합니다."}"""));
        jdbc.update("UPDATE stamp SET verify_status = 'PENDING', pending_reason = 'TRAVEL_TIME' WHERE stamp_id = ?", last);
        mvc.perform(adminPost("/api/admin/stamps/" + last + "/review", """
                {"decision":"APPROVE"}"""));

        assertThat(ebookCount(null)).as("전자책이 늘지 않았다").isEqualTo(before);
        assertThat(ebookCount("HOEHYANG")).isEqualTo(1);
    }

    /* ---------------- 흐름 ---------------- */

    /** 4칸은 직접 채우고 마지막 한 칸만 실제 3단계를 태운다 — 완주 연쇄를 진짜로 통과시키기 위해. */
    private void completeWholeCourse() throws Exception {
        for (int p = 1; p <= 4; p++) {
            jdbc.update("""
                    INSERT INTO stamp (pilgrimage_id, course_site_id, site_id, verify_status, verify_method,
                                       gps_verified_at, qr_verified_at, mission_verified_at, user_sentence)
                    VALUES (?, ?, ?, 'COMPLETED', 'GPS_QR',
                            NOW() - INTERVAL 3 HOUR, NOW() - INTERVAL 3 HOUR, NOW() - INTERVAL 3 HOUR, ?)""",
                    pilgrimageId, slot[p], site[p], SENTENCE);
        }
        jdbc.update("""
                INSERT IGNORE INTO slot_site (course_site_id, site_id, track, sort_no)
                VALUES (?, ?, 'MAIN', 1)""", slot[5], site[5]);

        String body = mvc.perform(userPost("/api/stamps/" + slot[5] + "/gps-check", """
                        {"siteId":%d,"withinRadius":true,"accuracyGrade":"HIGH"}""".formatted(site[5])))
                .andReturn().getResponse().getContentAsString();
        long stampId = om.readTree(body).path("data").path("stampId").asLong();

        String qr = om.readTree(mvc.perform(adminPost("/api/admin/sites/" + site[5] + "/qr", "{}"))
                .andReturn().getResponse().getContentAsString()).path("data").path("qrToken").asText();
        mvc.perform(userPost("/api/stamps/" + stampId + "/qr", """
                {"qrToken":"%s"}""".formatted(qr)));
        mvc.perform(userPost("/api/stamps/" + stampId + "/mission", """
                {"sentence":"%s"}""".formatted(SENTENCE)));
    }

    /** 코스 1 을 뺀 나머지 ACTIVE 코스를 완주한 순례로 채운다. */
    private void completeEveryOtherActiveCourse() {
        List<Map<String, Object>> courses = jdbc.queryForList(
                "SELECT course_id FROM course WHERE status = 'ACTIVE' AND course_id <> ?", COURSE_ID);
        for (var c : courses) {
            long courseId = ((Number) c.get("course_id")).longValue();
            jdbc.update("""
                    INSERT INTO pilgrimage (user_id, course_id, status, started_at, completed_at)
                    VALUES (?, ?, 'COMPLETED', NOW(), NOW())""", userId, courseId);
        }
    }

    /** 시드 코스 12개를 잠시 ACTIVE 로. status 만 바꾸므로 tearDown 이 그대로 되돌린다. */
    private void openSeedCourses() {
        jdbc.update("UPDATE course SET status = 'ACTIVE' WHERE name LIKE '%공양의 길'");
    }

    /* ---------------- 조회·정리 ---------------- */

    private Integer activeCourses() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM course WHERE status = 'ACTIVE'", Integer.class);
    }

    private String pilgrimageStatus() {
        return jdbc.queryForObject("SELECT status FROM pilgrimage WHERE pilgrimage_id = ?", String.class, pilgrimageId);
    }

    private Integer certCount(String type) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM certificate WHERE user_id = ? AND cert_type = ?", Integer.class, userId, type);
    }

    private Integer ebookCount(String type) {
        return type == null
                ? jdbc.queryForObject("SELECT COUNT(*) FROM ebook WHERE user_id = ?", Integer.class, userId)
                : jdbc.queryForObject("SELECT COUNT(*) FROM ebook WHERE user_id = ? AND ebook_type = ?",
                        Integer.class, userId, type);
    }

    private Integer rewardCount(String trigger) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_reward ur JOIN reward_policy rp ON rp.reward_policy_id = ur.reward_policy_id
                 WHERE ur.user_id = ? AND rp.trigger_type = ?""", Integer.class, userId, trigger);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder userPost(String url, String body) {
        return post(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder adminPost(String url, String body) {
        return post(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"%s"}""".formatted(email, password)))
                .andReturn().getResponse().getContentAsString();
        JsonNode n = om.readTree(body);
        return n.path("data").path("accessToken").asText();
    }

    private void cleanUp() {
        String in = "(SELECT user_id FROM users WHERE email = ?)";
        // 배송 정보가 user_reward 를 RESTRICT 로 잡는다 — 먼저 지운다(챕터 7 보강 B-4).
        jdbc.update("DELETE FROM reward_claim WHERE user_reward_id IN (SELECT user_reward_id FROM user_reward WHERE user_id IN " + in + ")", EMAIL);
        jdbc.update("DELETE FROM user_reward WHERE user_id IN " + in, EMAIL);
        jdbc.update("DELETE FROM certificate WHERE user_id IN " + in, EMAIL);
        jdbc.update("DELETE FROM ebook WHERE user_id IN " + in, EMAIL);
        jdbc.update("DELETE FROM thinkbox WHERE user_id IN " + in, EMAIL);
        jdbc.update("DELETE FROM stamp WHERE pilgrimage_id IN (SELECT pilgrimage_id FROM pilgrimage WHERE user_id IN " + in + ")", EMAIL);
        jdbc.update("DELETE FROM pilgrimage WHERE user_id IN " + in, EMAIL);
        jdbc.update("DELETE FROM phrase_seen WHERE user_id IN " + in, EMAIL);
        jdbc.update("DELETE FROM task_seen WHERE user_id IN " + in, EMAIL);
        jdbc.update("DELETE FROM refresh_token WHERE user_id IN " + in, EMAIL);
        jdbc.update("DELETE FROM user_agreement WHERE user_id IN " + in, EMAIL);
        jdbc.update("DELETE FROM users WHERE email = ?", EMAIL);
        // 시드 코스는 DRAFT 가 정상 상태다. 열어 둔 채로 두면 다음 테스트·검증 세트가 흔들린다.
        jdbc.update("UPDATE course SET status = 'DRAFT' WHERE name LIKE '%공양의 길'");
        jdbc.update("UPDATE site SET qr_version = 1 WHERE site_id <= 5");
    }
}
