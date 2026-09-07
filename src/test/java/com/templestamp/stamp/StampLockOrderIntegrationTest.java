package com.templestamp.stamp;

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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 잠금 순서 — 사용자의 다짐 제출과 관리자의 심사 승인이 <b>동시에</b> 들어와도 교착하지 않아야 한다.
 * <p>
 * 둘 다 {@code pilgrimage → stamp} 순서로 잠근다. 한쪽이라도 순서를 뒤집으면 서로가 상대의 첫 잠금을
 * 기다려 MySQL 이 한쪽을 deadlock 으로 죽인다(또는 60초 lock wait timeout 뒤 500).
 * 이 테스트는 그 순서가 지켜지는지를 <b>실제 두 스레드로</b> 확인한다 — 코드를 읽어서는 알 수 없다.
 * <p>
 * 서로 다른 두 도장을 대상으로 한다. 같은 행이면 그냥 순서대로 처리될 뿐이고,
 * 교착은 "A 가 1을 잡고 2를 기다리는 동안 B 가 2를 잡고 1을 기다릴 때" 생기기 때문이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StampLockOrderIntegrationTest {

    private static final long COURSE_ID = 1L;
    private static final String EMAIL = "lock-order@test.com";
    private static final String PASSWORD = "Test1234!";
    private static final String SENTENCE = "오늘 받은 것들의 이름을 하나씩 불러본다.";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;

    private String token;
    private String adminToken;
    private long userId;
    private long[] slot = new long[6];
    private long[] site = new long[6];

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
        adminToken = login("admin@templestamp.local", "Admin1234!");
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"%s","nickname":"잠금순서"}""".formatted(EMAIL, PASSWORD)));
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
            jdbc.update("""
                    INSERT IGNORE INTO slot_site (course_site_id, site_id, track, sort_no)
                    VALUES (?, ?, 'MAIN', 1)""", slot[p], site[p]);
        }
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("사용자 미션 제출과 관리자 승인이 동시에 들어와도 교착 없이 둘 다 끝난다")
    void concurrent_mission_and_review_do_not_deadlock() throws Exception {
        // 자리 1 — 사용자가 곧 다짐을 낼 도장 (QR_DONE 까지)
        long mine = gpsThenQr(1);

        // 자리 2 — 관리자가 곧 승인할 보류 도장. 같은 순례라 pilgrimage 행이 겹친다(교착 조건)
        long pending = gpsThenQr(2);
        jdbc.update("""
                UPDATE stamp SET verify_status = 'PENDING', pending_reason = 'TRAVEL_TIME',
                                 user_sentence = ?, mission_verified_at = NULL
                 WHERE stamp_id = ?""", SENTENCE, pending);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> submit = () -> mvc.perform(post("/api/stamps/" + mine + "/mission")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"sentence":"%s"}""".formatted(SENTENCE)))
                    .andReturn().getResponse().getStatus();

            Callable<Integer> approve = () -> mvc.perform(post("/api/admin/stamps/" + pending + "/review")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"decision":"APPROVE"}"""))
                    .andReturn().getResponse().getStatus();

            Future<Integer> a = pool.submit(submit);
            Future<Integer> b = pool.submit(approve);

            // 교착이면 MySQL 의 lock wait timeout(기본 50초)까지 매달린다. 20초를 넘기면 실패로 본다.
            int submitStatus = a.get(20, TimeUnit.SECONDS);
            int approveStatus = b.get(20, TimeUnit.SECONDS);

            assertThat(submitStatus).as("다짐 제출").isEqualTo(200);
            assertThat(approveStatus).as("관리자 승인").isEqualTo(200);
        } finally {
            pool.shutdownNow();
        }

        assertThat(status(mine)).isEqualTo("COMPLETED");
        assertThat(status(pending)).isEqualTo("COMPLETED");
    }

    /* ---------------- 내부 ---------------- */

    private long gpsThenQr(int position) throws Exception {
        String body = mvc.perform(post("/api/stamps/" + slot[position] + "/gps-check")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"siteId":%d,"withinRadius":true,"accuracyGrade":"HIGH"}""".formatted(site[position])))
                .andReturn().getResponse().getContentAsString();
        long stampId = om.readTree(body).path("data").path("stampId").asLong();

        String qrBody = mvc.perform(post("/api/admin/sites/" + site[position] + "/qr")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getContentAsString();
        String qrToken = om.readTree(qrBody).path("data").path("qrToken").asText();

        mvc.perform(post("/api/stamps/" + stampId + "/qr")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"qrToken":"%s"}""".formatted(qrToken)));
        return stampId;
    }

    private String status(long stampId) {
        return jdbc.queryForObject("SELECT verify_status FROM stamp WHERE stamp_id = ?", String.class, stampId);
    }

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"%s"}""".formatted(email, password)))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body).path("data").path("accessToken").asText();
    }

    private void cleanUp() {
        String in = "(SELECT user_id FROM users WHERE email = ?)";
        // 배송 정보가 user_reward 를 RESTRICT 로 잡는다 — 먼저 지운다(챕터 7 보강 B-4).
        jdbc.update("DELETE FROM reward_claim WHERE user_reward_id IN (SELECT user_reward_id FROM user_reward WHERE user_id IN " + in + ")", EMAIL);
        jdbc.update("DELETE FROM user_reward WHERE user_id IN " + in, EMAIL);
        jdbc.update("DELETE FROM thinkbox WHERE user_id IN " + in, EMAIL);
        jdbc.update("DELETE FROM stamp WHERE pilgrimage_id IN (SELECT pilgrimage_id FROM pilgrimage WHERE user_id IN " + in + ")", EMAIL);
        jdbc.update("DELETE FROM pilgrimage WHERE user_id IN " + in, EMAIL);
        jdbc.update("DELETE FROM phrase_seen WHERE user_id IN " + in, EMAIL);
        jdbc.update("DELETE FROM task_seen WHERE user_id IN " + in, EMAIL);
        jdbc.update("DELETE FROM refresh_token WHERE user_id IN " + in, EMAIL);
        jdbc.update("DELETE FROM user_agreement WHERE user_id IN " + in, EMAIL);
        jdbc.update("DELETE FROM users WHERE email = ?", EMAIL);
        // slot_site 는 data.sql 이 넣는 데모 행이라 지우지 않는다 — 지우면 다음 실행이 COURSE-4001 로 막힌다.
        jdbc.update("UPDATE site SET qr_version = 1 WHERE site_id <= 5");
    }
}
