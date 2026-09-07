package com.templestamp.stamp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 다짐 제출 경로의 동시성(챕터 7 보강 B-2). <b>실제 HTTP 경로</b>로 두 스레드를 동시에 태운다.
 * <p>
 * 이 경로만 완주 연쇄가 도장 트랜잭션 <b>안</b>에 있다 — 응답에 인증서 번호와 보상을 실어 보내야 해서
 * 커밋 뒤로 미룰 수 없다(보강 A §2-3 ①). 그래서 여기서만 잠금 순서가 문제가 된다:
 * {@code user → stamp/slot → completion → certificate/reward}.
 * <p>
 * 서비스 메서드를 직접 부르는 {@link CompletionServiceTest} 의 동시성 테스트와 다른 것을 본다.
 * 그쪽은 완주 연쇄만, 이쪽은 <b>GPS·QR 검증과 도장 잠금까지 포함한 요청 전체</b>다.
 * 5회 반복하는 이유는 교착이 매번 나지 않기 때문이다 — 한 번 통과한 것은 증거가 못 된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MissionConcurrencyIntegrationTest {

    private static final long COURSE_ID = 1L;
    private static final String PASSWORD = "Test1234!";
    private static final String EMAIL_A = "mission-conc-a@test.com";
    private static final String EMAIL_B = "mission-conc-b@test.com";
    private static final int TIMEOUT_SECONDS = 20;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;

    private String adminToken;
    private final long[] slot = new long[6];
    private final long[] site = new long[6];

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
        adminToken = login("admin@templestamp.local", "Admin1234!");
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT course_site_id, site_id, position FROM course_site WHERE course_id = ? ORDER BY position",
                COURSE_ID)) {
            int p = ((Number) row.get("position")).intValue();
            slot[p] = ((Number) row.get("course_site_id")).longValue();
            site[p] = ((Number) row.get("site_id")).longValue();
            // 대표 사찰을 그 자리의 후보로 넣어 둔다(v4). 다른 테스트가 후보를 지웠다 다시 넣는 사이에
            // 걸리면 도착 확인이 COURSE-4001 로 막힌다 — 이 테스트가 자기 전제를 스스로 세운다.
            jdbc.update("""
                    INSERT IGNORE INTO slot_site (course_site_id, site_id, track, sort_no)
                    VALUES (?, ?, 'MAIN', 1)""", slot[p], site[p]);
        }
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @RepeatedTest(5)
    @DisplayName("같은 코스를 걷는 두 사람이 동시에 마지막 다짐을 내도 각자 완주 1건·인증서 1장")
    void two_users_finishing_the_same_course_at_once() throws Exception {
        String tokenA = newUser(EMAIL_A, "동시가");
        String tokenB = newUser(EMAIL_B, "동시나");
        long userA = userId(EMAIL_A);
        long userB = userId(EMAIL_B);

        long pilgrimageA = startWithFourDone(tokenA, userA);
        long pilgrimageB = startWithFourDone(tokenB, userB);
        long stampA = gpsThenQr(tokenA, 5);
        long stampB = gpsThenQr(tokenB, 5);

        int[] status = submitBoth(tokenA, stampA, tokenB, stampB);

        assertThat(status[0]).as("A 의 다짐 제출").isEqualTo(200);
        assertThat(status[1]).as("B 의 다짐 제출").isEqualTo(200);
        assertThat(pilgrimageStatus(pilgrimageA)).isEqualTo("COMPLETED");
        assertThat(pilgrimageStatus(pilgrimageB)).isEqualTo("COMPLETED");
        assertThat(validCertificates(userA)).as("A 의 유효 인증서").isEqualTo(1);
        assertThat(validCertificates(userB)).as("B 의 유효 인증서").isEqualTo(1);
        assertThat(duplicatedSerials()).as("번호 중복").isZero();
        assertThat(courseRewards(userA)).as("A 의 코스 완주 보상").isEqualTo(1);
        assertThat(courseRewards(userB)).as("B 의 코스 완주 보상").isEqualTo(1);
    }

    @RepeatedTest(5)
    @DisplayName("한 사람이 두 자리에 동시에 다짐을 내도 결과가 찢어지지 않는다 — 인증서는 많아야 한 장")
    void one_user_two_slots_at_once() throws Exception {
        String token = newUser(EMAIL_A, "동시한사람");
        long userId = userId(EMAIL_A);

        // 자리 1~3 은 어제 끝냈고, 4·5 를 동시에 낸다.
        long pilgrimageId = startWithDone(token, userId, 3);
        long stamp4 = gpsThenQr(token, 4);
        long stamp5 = gpsThenQr(token, 5);

        int[] status = submitBoth(token, stamp4, token, stamp5);

        assertThat(status[0]).as("자리 4 다짐 제출").isEqualTo(200);
        assertThat(status[1]).as("자리 5 다짐 제출").isEqualTo(200);

        // ★ 두 도장이 다 완료되지는 않는다. 잠금 순서 때문에 두 요청이 한 줄로 서고,
        //   뒤에 온 쪽은 방금 찍힌 도장을 직전 도장으로 보게 되어 이동시간 규칙에 걸린다
        //   — 같은 사람이 같은 분(分)에 두 절을 끝낼 수는 없으므로 그것이 옳은 동작이다.
        //   여기서 볼 것은 "몇 개가 완료됐나" 가 아니라 결과가 찢어지지 않는가다.
        int completed = completedStamps(pilgrimageId);
        assertThat(completed).as("완료 도장").isBetween(4, 5);

        boolean courseDone = completed == 5;
        assertThat(pilgrimageStatus(pilgrimageId))
                .as("완주 여부는 완료 도장 수와 어긋나지 않는다")
                .isEqualTo(courseDone ? "COMPLETED" : "IN_PROGRESS");
        assertThat(validCertificates(userId)).as("유효 인증서").isEqualTo(courseDone ? 1 : 0);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM certificate WHERE user_id = ?
                """, Integer.class, userId)).as("인증서 행 전체(회수본 포함) — 두 장이 생기면 안 된다")
                .isEqualTo(courseDone ? 1 : 0);
        assertThat(courseRewards(userId)).as("코스 완주 보상").isEqualTo(courseDone ? 1 : 0);
        assertThat(duplicatedSerials()).as("번호 중복").isZero();
    }

    /* ---------------- 내부 ---------------- */

    /** 두 요청을 같은 순간에 풀어 준다. 순서대로 돌면 동시성 검사가 아니다. */
    private int[] submitBoth(String tokenA, long stampA, String tokenB, long stampB) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier gate = new CyclicBarrier(2);
        try {
            Callable<Integer> a = () -> { gate.await(10, TimeUnit.SECONDS); return mission(tokenA, stampA); };
            Callable<Integer> b = () -> { gate.await(10, TimeUnit.SECONDS); return mission(tokenB, stampB); };
            Future<Integer> fa = pool.submit(a);
            Future<Integer> fb = pool.submit(b);
            // 교착이면 innodb_lock_wait_timeout(기본 50초)까지 매달린다. 20초를 넘기면 실패로 본다.
            return new int[] { fa.get(TIMEOUT_SECONDS, TimeUnit.SECONDS), fb.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) };
        } finally {
            pool.shutdownNow();
        }
    }

    private int mission(String token, long stampId) throws Exception {
        return mvc.perform(post("/api/stamps/" + stampId + "/mission")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sentence":"오늘 받은 것들의 이름을 하나씩 불러본다."}"""))
                .andReturn().getResponse().getStatus();
    }

    private long startWithFourDone(String token, long userId) throws Exception {
        return startWithDone(token, userId, 4);
    }

    /**
     * 자리 1..count 를 이미 끝낸 상태로 만든다.
     * <p>
     * 시각을 뒤로 미뤄 두는 것이 요점이다 — 방금 찍은 도장이 있으면 이동시간 검사에 걸려
     * 다음 다짐이 완료가 아니라 보류로 떨어지고, 그러면 동시성이 아니라 다른 것을 보게 된다.
     * 어제 날짜로 두면 하루 5개 한도도 비켜 간다.
     */
    private long startWithDone(String token, long userId, int count) throws Exception {
        jdbc.update("INSERT INTO pilgrimage (user_id, course_id, status) VALUES (?, ?, 'IN_PROGRESS')",
                userId, COURSE_ID);
        long pilgrimageId = jdbc.queryForObject(
                "SELECT pilgrimage_id FROM pilgrimage WHERE user_id = ? AND course_id = ?",
                Long.class, userId, COURSE_ID);
        for (int p = 1; p <= count; p++) {
            jdbc.update("""
                    INSERT INTO stamp (pilgrimage_id, course_site_id, site_id, verify_status, verify_method,
                                       user_sentence, gps_verified_at, mission_verified_at, created_at)
                    VALUES (?, ?, ?, 'COMPLETED', 'GPS_QR', '어제 걸었다.',
                            NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 6 HOUR, NOW() - INTERVAL 1 DAY)
                    """, pilgrimageId, slot[p], site[p]);
        }
        return pilgrimageId;
    }

    private long gpsThenQr(String token, int position) throws Exception {
        String body = mvc.perform(post("/api/stamps/" + slot[position] + "/gps-check")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"siteId":%d,"withinRadius":true,"accuracyGrade":"HIGH"}"""
                                .formatted(site[position])))
                .andReturn().getResponse().getContentAsString();
        long stampId = om.readTree(body).path("data").path("stampId").asLong();
        // 여기서 막히면 뒤의 단언이 엉뚱한 것을 가리킨다 — 응답을 그대로 들고 실패한다.
        assertThat(stampId).as("자리 " + position + " 도착 확인 실패: " + body).isPositive();

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

    private String newUser(String email, String nickname) throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"%s","nickname":"%s"}""".formatted(email, PASSWORD, nickname)));
        String token = login(email, PASSWORD);
        mvc.perform(post("/api/users/me/agreements")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        [{"agreementType":"LOCATION_SERVICE","version":1}]"""));
        return token;
    }

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"%s"}""".formatted(email, password)))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body).path("data").path("accessToken").asText();
    }

    private long userId(String email) {
        return jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, email);
    }

    private String pilgrimageStatus(long pilgrimageId) {
        return jdbc.queryForObject("SELECT status FROM pilgrimage WHERE pilgrimage_id = ?", String.class, pilgrimageId);
    }

    private int completedStamps(long pilgrimageId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM stamp WHERE pilgrimage_id = ? AND verify_status = 'COMPLETED'",
                Integer.class, pilgrimageId);
    }

    private int validCertificates(long userId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM certificate WHERE user_id = ? AND status = 'VALID'", Integer.class, userId);
    }

    private int duplicatedSerials() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) - COUNT(DISTINCT serial_no) FROM certificate", Integer.class);
    }

    private int courseRewards(long userId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_reward ur
                       JOIN reward_policy rp ON rp.reward_policy_id = ur.reward_policy_id
                 WHERE ur.user_id = ? AND rp.trigger_type = 'COURSE_COMPLETED'
                """, Integer.class, userId);
    }

    private void cleanUp() {
        for (String email : List.of(EMAIL_A, EMAIL_B)) {
            String in = "(SELECT user_id FROM users WHERE email = '" + email + "')";
            // 배송 정보가 user_reward 를 RESTRICT 로 잡는다 — 먼저 지운다(챕터 7 보강 B-4).
            jdbc.update("DELETE FROM reward_claim WHERE user_reward_id IN (SELECT user_reward_id FROM user_reward WHERE user_id IN " + in + ")");
            jdbc.update("DELETE FROM user_reward WHERE user_id IN " + in);
            jdbc.update("DELETE FROM certificate WHERE user_id IN " + in);
            jdbc.update("DELETE FROM ebook WHERE user_id IN " + in);
            jdbc.update("DELETE FROM thinkbox WHERE user_id IN " + in);
            jdbc.update("DELETE FROM stamp WHERE pilgrimage_id IN (SELECT pilgrimage_id FROM pilgrimage WHERE user_id IN " + in + ")");
            jdbc.update("DELETE FROM phrase_seen WHERE user_id IN " + in);
            jdbc.update("DELETE FROM task_seen WHERE user_id IN " + in);
            jdbc.update("DELETE FROM pilgrimage WHERE user_id IN " + in);
            jdbc.update("DELETE FROM refresh_token WHERE user_id IN " + in);
            jdbc.update("DELETE FROM user_agreement WHERE user_id IN " + in);
            jdbc.update("DELETE FROM users WHERE email = '" + email + "'");
        }
        jdbc.update("UPDATE site SET qr_version = 1 WHERE site_id <= 5");
    }
}
