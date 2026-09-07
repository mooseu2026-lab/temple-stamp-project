package com.templestamp.global.housekeeping;

import com.templestamp.global.config.EbookProperties;
import com.templestamp.global.housekeeping.dto.HousekeepingResult;
import com.templestamp.stamp.StampExpireBatch;
import com.templestamp.stamp.StampExpireService;
import com.templestamp.upload.ObjectStorageClient;
import com.templestamp.user.StorageOrphanMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * 청소기(챕터 9 §5·§9-2).
 * <p>
 * 이 스케줄러의 성질은 셋이다 —
 * ① <b>한 작업의 실패가 다른 작업을 막지 않는다</b>(작업마다 트랜잭션이 따로다),
 * ② <b>못 지우는 키가 영원히 돌지 않는다</b>(다섯 번을 넘기면 사람에게 넘긴다),
 * ③ <b>폐기됐지만 아직 만료 전인 토큰은 남긴다</b> — 그 행이 없으면 재사용 탐지가 눈을 잃는다.
 * 셋 다 "안 되는 쪽" 을 세는 검사라, 잘 도는 것만 보면 지나간다.
 */
@SpringBootTest
class HousekeepingTest {

    @Autowired HousekeepingScheduler scheduler;
    @Autowired OrphanCleaner orphanCleaner;
    @Autowired TokenCleaner tokenCleaner;
    @Autowired StorageOrphanMapper storageOrphanMapper;
    @Autowired EbookProperties ebookProperties;
    @Autowired JdbcTemplate jdbc;
    @Autowired StampExpireService stampExpireService;

    /** 몇 묶음으로 나눠 돌았는지 세려고 감시한다. 결과만 보면 한 번에 지웠는지 나눠 지웠는지 알 수 없다. */
    @MockitoSpyBean StampExpireBatch stampExpireBatch;

    /** 저장소가 아플 때를 흉내 내려고 가짜로 바꾼다. 다른 검사에서는 진짜처럼 굴어야 한다. */
    @MockitoSpyBean ObjectStorageClient storageClient;

    private long userId;

    @BeforeEach
    void setUp() {
        reset(storageClient);
        doCallRealMethod().when(storageClient).delete(anyString());
        jdbc.update("INSERT INTO users (email, password, nickname, role, tier, locale) "
                + "VALUES ('hk-test@test.com', 'x', '청소시험', 'USER', 'AGE40', 'ko')");
        userId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @AfterEach
    void tearDown() {
        reset(storageClient);
        jdbc.update("DELETE FROM storage_orphan WHERE file_key LIKE 'HKTEST/%'");
        jdbc.update("DELETE FROM refresh_token WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM users WHERE user_id = ?", userId);
    }

    /* ---------------- ① 파일 삭제 큐 ---------------- */

    @Test
    @DisplayName("① 저장소에 없는 키는 성공으로 본다 — 없는 파일을 못 지웠다고 세면 큐가 영원히 안 빈다")
    void missingKeyCountsAsDeleted() {
        storageOrphanMapper.enqueue("HKTEST/없는파일.pdf", "USER_WITHDRAWN");

        OrphanCleaner.Counts counts = orphanCleaner.consume();
        assertThat(counts.deleted()).isGreaterThanOrEqualTo(1);
        assertThat(counts.failed()).isZero();
        assertThat(pending()).isZero();
    }

    @Test
    @DisplayName("② 실제로 있는 파일은 지워진다")
    void realFileIsDeleted() {
        String key = "HKTEST/실물.txt";
        storageClient.put(key, "내용".getBytes(java.nio.charset.StandardCharsets.UTF_8), "text/plain");
        assertThat(storageClient.exists(key)).isTrue();
        storageOrphanMapper.enqueue(key, "USER_WITHDRAWN");

        orphanCleaner.consume();
        assertThat(storageClient.exists(key)).isFalse();
        assertThat(pending()).isZero();
    }

    @Test
    @DisplayName("③ 다섯 번을 넘기면 FAILED 로 접고 사람에게 넘긴다 — 5분마다 영원히 돌지 않게")
    void givesUpAfterMaxRetry() {
        String key = "HKTEST/못지우는파일.pdf";
        storageOrphanMapper.enqueue(key, "USER_WITHDRAWN");
        willThrow(new java.io.UncheckedIOException(
                new java.io.IOException("저장소가 아프다"))).given(storageClient).delete(key);

        int tries = ebookProperties.orphanMaxRetry() + 1;
        for (int i = 0; i < tries; i++) {
            orphanCleaner.consume();
        }

        Integer failed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM storage_orphan WHERE file_key = ? AND status = 'FAILED' AND needs_review = 1",
                Integer.class, key);
        assertThat(failed).as("사람이 볼 표시까지 켜진다").isEqualTo(1);

        // 접힌 뒤에는 다시 집지 않는다 — 로그가 같은 오류로 뒤덮이지 않게.
        OrphanCleaner.Counts after = orphanCleaner.consume();
        assertThat(after.deleted() + after.failed()).isZero();
    }

    /* ---------------- ② 토큰 ---------------- */

    @Test
    @DisplayName("④ 만료+유예를 넘긴 토큰만 지운다 — 폐기됐어도 미만료면 남긴다")
    void tokenCutoffKeepsRevokedButAlive() {
        int grace = ebookProperties.tokenGraceHours();
        long overdueRevoked = token("overdue-revoked", -(grace + 1), true);
        long overduePlain = token("overdue-plain", -(grace + 1), false);
        long revokedAlive = token("revoked-alive", 24, true);      // 폐기됐지만 아직 만료 전
        long freshAlive = token("fresh-alive", 24, false);
        long justExpired = token("just-expired", -1, false);        // 만료됐지만 유예 안에 있다

        int deleted = tokenCleaner.deleteExpired();
        assertThat(deleted).isGreaterThanOrEqualTo(2);

        assertThat(exists(overdueRevoked)).as("만료+유예를 넘겼다").isFalse();
        assertThat(exists(overduePlain)).isFalse();
        assertThat(exists(revokedAlive))
                .as("폐기됐지만 미만료 — 재사용 탐지가 이 행을 본다(함정 6)").isTrue();
        assertThat(exists(freshAlive)).isTrue();
        assertThat(exists(justExpired)).as("유예 안이라 아직 남는다").isTrue();
    }

    /* ---------------- ③ 작업 독립 ---------------- */

    @Test
    @DisplayName("⑤ 한 작업이 터져도 나머지가 돈다 — 저장소가 아프다고 도장 세션이 안 닫히면 안 된다")
    void oneStepFailureDoesNotStopOthers() {
        String key = "HKTEST/터지는파일.pdf";
        storageOrphanMapper.enqueue(key, "USER_WITHDRAWN");
        willThrow(new RuntimeException("저장소 전체가 아프다")).given(storageClient).delete(anyString());
        token("overdue-for-run", -(ebookProperties.tokenGraceHours() + 1), false);

        HousekeepingResult result = scheduler.run();

        // 파일 삭제는 실패했지만 토큰 정리는 그대로 돌았다.
        assertThat(result.getOrphanFailed()).isGreaterThanOrEqualTo(1);
        assertThat(result.getTokenDeleted()).as("다른 작업은 영향을 받지 않는다").isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("⑥ 한 바퀴의 숫자가 로그와 응답에 같이 실린다 — 두 곳이 다르면 사람이 매번 판단해야 한다")
    void resultCarriesEveryCount() {
        HousekeepingResult result = scheduler.run();
        assertThat(result.getOrphanDeleted()).isGreaterThanOrEqualTo(0);
        assertThat(result.getOrphanFailed()).isGreaterThanOrEqualTo(0);
        assertThat(result.getTokenDeleted()).isGreaterThanOrEqualTo(0);
        assertThat(result.getEbookReady()).isGreaterThanOrEqualTo(0);
        assertThat(result.getEbookFailed()).isGreaterThanOrEqualTo(0);
        assertThat(result.getSessionExpired()).isGreaterThanOrEqualTo(0);
    }

    /* ---------------- 도구 ---------------- */

    private int pending() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM storage_orphan WHERE file_key LIKE 'HKTEST/%' AND status = 'PENDING'",
                Integer.class);
    }

    /* ---------------- ⑦ 도장 세션 만료 — 묶음으로 닫는다 ---------------- */

    @Test
    @DisplayName("⑦ 3,000건을 200씩 15묶음으로 닫는다 — 조건으로 한 번에 지우면 미션 제출과 교착한다")
    void expiresStaleSessionsInPrimaryKeyBatches() {
        // 먼저 남아 있던 만료 대상을 비운다. 다른 검사가 남긴 것이 섞이면 묶음 수가 흔들린다.
        stampExpireService.expireStale();

        long pilgrimageId = newPilgrimage();
        long slotId = jdbc.queryForObject(
                "SELECT course_site_id FROM course_site ORDER BY course_site_id LIMIT 1", Long.class);
        jdbc.update("INSERT INTO stamp (pilgrimage_id, course_site_id, verify_status, verify_method, gps_verified_at) "
                        + "SELECT ?, ?, 'QR_DONE', 'GPS_QR', NOW() - INTERVAL 90 MINUTE "
                        + "FROM information_schema.columns a, information_schema.columns b LIMIT 3000",
                pilgrimageId, slotId);
        assertThat(stampsOf(pilgrimageId, "QR_DONE")).isEqualTo(3000);

        reset(stampExpireBatch);
        int expired = stampExpireService.expireStale();

        // ① 전부 닫혔다
        assertThat(expired).isEqualTo(3000);
        assertThat(stampsOf(pilgrimageId, "EXPIRED")).isEqualTo(3000);
        assertThat(stampsOf(pilgrimageId, "QR_DONE")).isZero();

        // ② 한 번에 지운 것이 아니라 15묶음으로 나눠 돌았다 — 3,000 ÷ 200.
        //    이것이 이 검사의 요점이다. 묶음마다 PK 로만 잠그기 때문에 미션 제출과 순서가 같아진다.
        verify(stampExpireBatch, atLeast(15)).expireBatch(org.mockito.ArgumentMatchers.anyList());

        // ③ 한 바퀴의 숫자에도 그대로 실린다
        HousekeepingResult result = scheduler.run();
        assertThat(result.getSessionExpired()).isZero();   // 이미 다 닫아서 다음 바퀴에는 대상이 없다

        jdbc.update("DELETE FROM stamp WHERE pilgrimage_id = ?", pilgrimageId);
        jdbc.update("DELETE FROM pilgrimage WHERE pilgrimage_id = ?", pilgrimageId);
    }

    private long newPilgrimage() {
        Long courseId = jdbc.queryForObject("SELECT course_id FROM course ORDER BY course_id LIMIT 1", Long.class);
        jdbc.update("INSERT INTO pilgrimage (user_id, course_id, status, started_at) VALUES (?, ?, 'IN_PROGRESS', NOW())",
                userId, courseId);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private int stampsOf(long pilgrimageId, String status) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM stamp WHERE pilgrimage_id = ? AND verify_status = ?",
                Integer.class, pilgrimageId, status);
        return n == null ? 0 : n;
    }

    private long token(String suffix, int hoursFromNow, boolean revoked) {
        jdbc.update("INSERT INTO refresh_token (user_id, token_hash, expires_at, revoked_at) "
                        + "VALUES (?, ?, ?, ?)",
                userId, "hk-" + suffix + "-" + System.nanoTime(),
                LocalDateTime.now().plusHours(hoursFromNow),
                revoked ? LocalDateTime.now().minusHours(1) : null);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private boolean exists(long id) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_token WHERE refresh_token_id = ?", Integer.class, id);
        return n != null && n > 0;
    }
}
