package com.templestamp.stamp;

import com.templestamp.certificate.CertificateMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 완주 재집계 규칙(챕터 7 §2). 도장 상태를 직접 만들어 놓고 재집계를 불러 결과를 본다.
 * <p>
 * HTTP 를 거치지 않는 이유는 이 규칙의 어려운 부분이 <b>경계와 되돌리기</b> 라서다 —
 * 4/5 와 5/5, 취소와 재승인, 하한 12. 그 조합을 요청으로 만들면 이동시간·하루 한도 같은
 * 다른 규칙에 먼저 걸려 무엇을 보고 있는지 흐려진다.
 */
@SpringBootTest
class CompletionServiceTest {

    private static final long COURSE_ID = 1L;
    private static final String EMAIL = "completion-test@test.com";

    @Autowired CompletionService completionService;
    @Autowired JdbcTemplate jdbc;
    @Autowired CertificateMapper certificateMapper;

    private long userId;
    private long pilgrimageId;
    private final long[] slot = new long[6];
    private final long[] site = new long[6];

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbc.update("""
                INSERT INTO users (email, password, nickname, role, tier, locale)
                VALUES (?, '$2a$10$FVVkQmIpuwsYytWwqTLv7exlCfmLsSNovNp13Bo6m.DrG8cLgZj1a', '완주시험', 'USER', 'AGE30', 'ko')
                """, EMAIL);
        userId = jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, EMAIL);
        jdbc.update("INSERT INTO pilgrimage (user_id, course_id, status) VALUES (?, ?, 'IN_PROGRESS')",
                userId, COURSE_ID);
        pilgrimageId = jdbc.queryForObject(
                "SELECT pilgrimage_id FROM pilgrimage WHERE user_id = ? AND course_id = ?",
                Long.class, userId, COURSE_ID);

        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT course_site_id, site_id, position FROM course_site WHERE course_id = ? ORDER BY position",
                COURSE_ID)) {
            int p = ((Number) row.get("position")).intValue();
            slot[p] = ((Number) row.get("course_site_id")).longValue();
            site[p] = ((Number) row.get("site_id")).longValue();
        }
    }

    @AfterEach
    void tearDown() {
        cleanUp();
        restoreCourses();
    }

    @Test
    @DisplayName("4/5 는 완주가 아니다 — 완주 행도 인증서도 생기지 않는다")
    void four_of_five_is_not_completed() {
        stampsCompleted(4);
        completionService.afterStampCompleted(userId, pilgrimageId, lastStampId());

        assertThat(pilgrimageStatus()).isEqualTo("IN_PROGRESS");
        assertThat(certCount("VALID")).isZero();
    }

    @Test
    @DisplayName("5/5 면 완주 행이 서고 코스 인증서가 한 장 나온다")
    void five_of_five_completes() {
        stampsCompleted(5);
        completionService.afterStampCompleted(userId, pilgrimageId, lastStampId());

        assertThat(pilgrimageStatus()).isEqualTo("COMPLETED");
        assertThat(certCount("VALID")).isEqualTo(1);
        assertThat(validSerial()).matches("^PG-\\d{4}-\\d{6}$");
    }

    @Test
    @DisplayName("몇 번을 다시 돌려도 결과가 같다 — 인증서도 보상도 늘지 않는다")
    void recount_is_idempotent() {
        stampsCompleted(5);
        completionService.afterStampCompleted(userId, pilgrimageId, lastStampId());
        int certs = certCount("VALID");
        int rewards = rewardCount();

        for (int i = 0; i < 3; i++) {
            completionService.afterStampCompleted(userId, pilgrimageId, lastStampId());
        }

        assertThat(certCount("VALID")).isEqualTo(certs);
        assertThat(rewardCount()).isEqualTo(rewards);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM pilgrimage WHERE user_id = ? AND course_id = ?",
                Integer.class, userId, COURSE_ID)).isEqualTo(1);
    }

    @Test
    @DisplayName("도장이 하나 빠지면 완주가 취소되고 인증서는 지워지지 않고 회수된다")
    void revoke_cancels_completion_and_revokes_certificate() {
        stampsCompleted(5);
        completionService.afterStampCompleted(userId, pilgrimageId, lastStampId());
        String issued = validSerial();

        rejectLastStamp();
        completionService.afterStampRevoked(pilgrimageId);

        assertThat(pilgrimageStatus()).isEqualTo("IN_PROGRESS");
        assertThat(certCount("VALID")).isZero();
        assertThat(certCount("REVOKED")).isEqualTo(1);
        // 행이 사라지면 공개 진위 확인에서 "그런 번호 없음" 이 된다. 남아 있어야 "무효" 를 말할 수 있다.
        assertThat(certificateMapper.findRowBySerial(issued)).isPresent();
        assertThat(jdbc.queryForObject(
                "SELECT revoke_reason FROM certificate WHERE serial_no = ?", String.class, issued))
                .isEqualTo("COMPLETION_CANCELED");
    }

    @Test
    @DisplayName("코스 하나를 완주해도 보상은 0이다 — 코스 쿠폰 정책이 꺼졌다(챕터 11 결정 A)")
    void single_course_grants_no_reward() {
        stampsCompleted(5);
        completionService.afterStampCompleted(userId, pilgrimageId, lastStampId());

        assertThat(pilgrimageStatus()).isEqualTo("COMPLETED");
        assertThat(rewardCount()).as("3코스 전에는 아무 사은품도 없다").isZero();
    }

    @Test
    @DisplayName("3코스마다 전자일기장 — 3에서 한 권, 4에서는 늘지 않고, 6에서 한 권 더")
    void diary_every_three_courses() {
        stampsCompleted(5);
        completeExtraCourses(3);          // 이 코스까지 합쳐 3코스
        completionService.afterStampCompleted(userId, pilgrimageId, lastStampId());
        assertThat(diaryRewards()).as("3코스 — 한 권").isEqualTo(1);
        assertThat(milestones()).containsExactly(3);
        assertThat(interimEbooks()).isEqualTo(1);

        completeExtraCourses(4);          // 4코스
        completionService.afterStampCompleted(userId, pilgrimageId, lastStampId());
        assertThat(diaryRewards()).as("4코스 — 늘지 않는다").isEqualTo(1);

        completeExtraCourses(6);          // 6코스
        completionService.afterStampCompleted(userId, pilgrimageId, lastStampId());
        assertThat(diaryRewards()).as("6코스 — 한 권 더").isEqualTo(2);
        assertThat(milestones()).containsExactly(3, 6);
        assertThat(interimEbooks()).as("전자책도 마일스톤마다").isEqualTo(2);
    }
    @Test
    @DisplayName("재승인하면 완주 행은 되살아나고 인증서는 새 번호로 나온다 — 옛 번호는 무효로 남는다")
    void re_approval_revives_row_and_issues_new_serial() {
        stampsCompleted(5);
        completionService.afterStampCompleted(userId, pilgrimageId, lastStampId());
        String first = validSerial();

        rejectLastStamp();
        completionService.afterStampRevoked(pilgrimageId);
        approveLastStamp();
        completionService.afterStampCompleted(userId, pilgrimageId, lastStampId());

        assertThat(pilgrimageStatus()).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM pilgrimage WHERE user_id = ? AND course_id = ?",
                Integer.class, userId, COURSE_ID)).as("완주 행은 여전히 하나").isEqualTo(1);
        assertThat(validSerial()).isNotEqualTo(first);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM certificate WHERE serial_no = ?", String.class, first)).isEqualTo("REVOKED");
    }

    @Test
    @DisplayName("회향에는 하한이 있다 — ACTIVE 코스가 1개뿐이면 성립하지 않는다")
    void hoehyang_needs_the_floor() {
        stampsCompleted(5);
        completionService.afterStampCompleted(userId, pilgrimageId, lastStampId());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM course WHERE status = 'ACTIVE'", Integer.class))
                .as("전제: ACTIVE 코스가 1개인 상태").isEqualTo(1);
        assertThat(hoehyangCount()).as("코스 하나를 끝냈다고 회향이 되면 안 된다").isZero();
    }

    @Test
    @DisplayName("ACTIVE 코스 13개를 전부 완주하면 회향 인증서가 나온다")
    void hoehyang_when_all_active_courses_are_done() {
        stampsCompleted(5);
        completionService.afterStampCompleted(userId, pilgrimageId, lastStampId());

        // 시드 코스를 잠깐 올리고, 그 코스들의 완주를 주입한다(tearDown 이 되돌린다).
        // 번호 범위(37~48)로 고르지 않는다 — 그 값은 개발 DB 의 auto_increment 가 그렇게 흘렀을 뿐이고
        // 새로 만든 DB 에서는 2~13 이다(최종 점검 F 의 빈 DB 재구축에서 드러났다).
        // "이 검사가 쓰는 코스가 아닌 나머지" 가 곧 시드 코스다.
        jdbc.update("UPDATE course SET status = 'ACTIVE' WHERE course_id <> ?", COURSE_ID);
        jdbc.update("""
                INSERT IGNORE INTO pilgrimage (user_id, course_id, status, completed_at)
                SELECT ?, course_id, 'COMPLETED', NOW() FROM course WHERE course_id <> ?
                """, userId, COURSE_ID);

        completionService.afterStampCompleted(userId, pilgrimageId, lastStampId());

        assertThat(hoehyangCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT serial_no FROM certificate
                 WHERE user_id = ? AND cert_type = 'HOEHYANG' AND status = 'VALID'
                """, String.class, userId)).matches("^HH-\\d{4}-\\d{6}$");
    }

    @Test
    @DisplayName("두 사용자의 완주 재집계가 동시에 들어와도 교착 없이 둘 다 끝난다")
    void concurrent_recount_does_not_deadlock() throws Exception {
        stampsCompleted(5);
        long otherUser = createOtherUser();
        long otherPilgrimage = jdbc.queryForObject(
                "SELECT pilgrimage_id FROM pilgrimage WHERE user_id = ? AND course_id = ?",
                Long.class, otherUser, COURSE_ID);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> a = pool.submit(() -> {
                completionService.afterStampCompleted(userId, pilgrimageId, lastStampId());
                return "ok";
            });
            Future<String> b = pool.submit(() -> {
                completionService.afterStampCompleted(otherUser, otherPilgrimage, lastStampId(otherPilgrimage));
                return "ok";
            });
            // 교착이면 lock wait timeout(기본 50초)까지 매달린다. 20초를 넘기면 실패로 본다.
            assertThat(a.get(20, TimeUnit.SECONDS)).isEqualTo("ok");
            assertThat(b.get(20, TimeUnit.SECONDS)).isEqualTo("ok");
        } finally {
            pool.shutdownNow();
        }

        assertThat(pilgrimageStatus()).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM pilgrimage WHERE pilgrimage_id = ?", String.class, otherPilgrimage))
                .isEqualTo("COMPLETED");
    }

    /* ---------------- 내부 ---------------- */

    /** 자리 1..n 에 완료 도장을 직접 만든다. GPS·QR·이동시간은 여기서 볼 것이 아니다. */
    private void stampsCompleted(int count) {
        for (int p = 1; p <= count; p++) {
            jdbc.update("""
                    INSERT INTO stamp (pilgrimage_id, course_site_id, site_id, verify_status,
                                       verify_method, user_sentence, mission_verified_at)
                    VALUES (?, ?, ?, 'COMPLETED', 'GPS_QR', '오늘 받은 것들의 이름을 하나씩 불러본다.', NOW())
                    """, pilgrimageId, slot[p], site[p]);
        }
    }

    private long createOtherUser() {
        String email = "completion-test2@test.com";
        jdbc.update("""
                INSERT INTO users (email, password, nickname, role, tier, locale)
                VALUES (?, '$2a$10$FVVkQmIpuwsYytWwqTLv7exlCfmLsSNovNp13Bo6m.DrG8cLgZj1a', '완주시험2', 'USER', 'AGE30', 'ko')
                """, email);
        Long other = jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, email);
        jdbc.update("INSERT INTO pilgrimage (user_id, course_id, status) VALUES (?, ?, 'IN_PROGRESS')", other, COURSE_ID);
        Long otherPilgrimage = jdbc.queryForObject(
                "SELECT pilgrimage_id FROM pilgrimage WHERE user_id = ? AND course_id = ?", Long.class, other, COURSE_ID);
        for (int p = 1; p <= 5; p++) {
            jdbc.update("""
                    INSERT INTO stamp (pilgrimage_id, course_site_id, site_id, verify_status,
                                       verify_method, user_sentence, mission_verified_at)
                    VALUES (?, ?, ?, 'COMPLETED', 'GPS_QR', '오늘 받은 것들의 이름을 하나씩 불러본다.', NOW())
                    """, otherPilgrimage, slot[p], site[p]);
        }
        return other;
    }

    private long lastStampId() {
        return lastStampId(pilgrimageId);
    }

    private long lastStampId(long pid) {
        return jdbc.queryForObject(
                "SELECT MAX(stamp_id) FROM stamp WHERE pilgrimage_id = ?", Long.class, pid);
    }

    private void rejectLastStamp() {
        jdbc.update("""
                UPDATE stamp SET verify_status = 'REJECTED', mission_verified_at = NULL
                 WHERE stamp_id = ?""", lastStampId());
    }

    private void approveLastStamp() {
        jdbc.update("""
                UPDATE stamp SET verify_status = 'COMPLETED', mission_verified_at = NOW()
                 WHERE stamp_id = ?""", lastStampId());
    }

    private String pilgrimageStatus() {
        return jdbc.queryForObject("SELECT status FROM pilgrimage WHERE pilgrimage_id = ?", String.class, pilgrimageId);
    }

    private int certCount(String status) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM certificate WHERE user_id = ? AND cert_type = 'PILGRIMAGE' AND status = ?",
                Integer.class, userId, status);
    }

    private int hoehyangCount() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM certificate WHERE user_id = ? AND cert_type = 'HOEHYANG' AND status = 'VALID'",
                Integer.class, userId);
    }

    private String validSerial() {
        return jdbc.queryForObject("""
                SELECT serial_no FROM certificate
                 WHERE user_id = ? AND cert_type = 'PILGRIMAGE' AND status = 'VALID'
                """, String.class, userId);
    }

    /** 이 검사의 코스 말고 <b>다른 코스</b>를 n-1 개 더 완주 상태로 만든다(합계 n 코스). */
    private void completeExtraCourses(int total) {
        jdbc.update("UPDATE course SET status = 'ACTIVE' WHERE course_id <> ?", COURSE_ID);
        jdbc.update("""
                INSERT IGNORE INTO pilgrimage (user_id, course_id, status, completed_at)
                SELECT ?, course_id, 'COMPLETED', NOW() FROM course
                 WHERE course_id <> ? ORDER BY course_id LIMIT ?
                """, userId, COURSE_ID, total - 1);
    }

    private int diaryRewards() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_reward ur JOIN reward_policy rp
                       ON rp.reward_policy_id = ur.reward_policy_id
                 WHERE ur.user_id = ? AND rp.trigger_type = 'EVERY_THREE_COURSES'
                """, Integer.class, userId);
    }

    private List<Integer> milestones() {
        return jdbc.queryForList("""
                SELECT ur.milestone FROM user_reward ur JOIN reward_policy rp
                       ON rp.reward_policy_id = ur.reward_policy_id
                 WHERE ur.user_id = ? AND rp.trigger_type = 'EVERY_THREE_COURSES'
                 ORDER BY ur.milestone
                """, Integer.class, userId);
    }

    private int interimEbooks() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM ebook WHERE user_id = ? AND ebook_type = 'INTERIM'",
                Integer.class, userId);
    }

    private int rewardCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM user_reward WHERE user_id = ?", Integer.class, userId);
    }

    private void restoreCourses() {
        // 시드 정본은 전부 DRAFT 다. 되돌리지 않으면 다음 테스트의 "하한" 전제가 조용히 깨진다.
        // 올릴 때와 <b>같은 조건</b>으로 되돌린다 — 한쪽만 번호 범위를 쓰면 되돌리기가 새어 나간다.
        jdbc.update("UPDATE course SET status = 'DRAFT' WHERE course_id <> ?", COURSE_ID);
    }

    private void cleanUp() {
        List<String> emails = List.of(EMAIL, "completion-test2@test.com");
        for (String email : emails) {
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
            jdbc.update("DELETE FROM user_agreement WHERE user_id IN " + in);
            jdbc.update("DELETE FROM users WHERE email = '" + email + "'");
        }
    }
}
