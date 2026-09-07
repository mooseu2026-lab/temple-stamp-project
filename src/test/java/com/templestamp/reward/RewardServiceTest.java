package com.templestamp.reward;

import com.templestamp.admin.dto.ClaimReviewRequest;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.reward.dto.ClaimRequest;
import com.templestamp.reward.dto.RewardResponse;
import com.templestamp.reward.dto.RewardRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 보상 규칙(챕터 7 §4). 적립 멱등 · claimable 계산 · 수령 신청 관문 · 상태 전이.
 * <p>
 * 이 챕터가 고친 결함 5-1 이 여기 있다 — 응답의 claimable 을 프론트가 보지 않고 눌러도
 * 서버가 다시 막아야 한다. 그래서 "목록의 claimable" 과 "신청의 판정" 이 같은 식에서 나오는지를 본다.
 */
@SpringBootTest
class RewardServiceTest {

    private static final String EMAIL = "reward-test@test.com";
    private static final String OTHER_EMAIL = "reward-test2@test.com";
    private static final long COURSE_ID = 1L;

    @Autowired RewardService rewardService;
    @Autowired UserRewardMapper userRewardMapper;
    @Autowired JdbcTemplate jdbc;

    private long userId;
    private long otherUserId;
    private long pilgrimageId;

    @BeforeEach
    void setUp() {
        cleanUp();
        userId = createUser(EMAIL, "보상시험");
        otherUserId = createUser(OTHER_EMAIL, "이웃");
        jdbc.update("INSERT INTO pilgrimage (user_id, course_id, status) VALUES (?, ?, 'COMPLETED')",
                userId, COURSE_ID);
        pilgrimageId = jdbc.queryForObject(
                "SELECT pilgrimage_id FROM pilgrimage WHERE user_id = ? AND course_id = ?",
                Long.class, userId, COURSE_ID);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("같은 근거로 백 번 적립해도 보상은 늘지 않는다")
    void grant_is_idempotent() {
        rewardService.grant(userId, RewardPolicy.ON_ALL_COMPLETED, null, pilgrimageId);
        int after1 = rewardCount();

        for (int i = 0; i < 5; i++) {
            rewardService.grant(userId, RewardPolicy.ON_ALL_COMPLETED, null, pilgrimageId);
        }

        assertThat(after1).isPositive();
        assertThat(rewardCount()).isEqualTo(after1);
    }

    @Test
    @DisplayName("claimable 은 실물이고, 신청할 수 있는 상태이고, 내 것일 때만 참이다")
    void claimable_has_three_conditions() {
        long physical = physicalReward();
        RewardRow row = userRewardMapper.findRowById(physical).orElseThrow();

        assertThat(RewardService.claimable(row, userId)).as("실물 · GRANTED · 본인").isTrue();
        assertThat(RewardService.claimable(row, otherUserId)).as("남의 것이면 거짓").isFalse();

        jdbc.update("UPDATE user_reward SET status = 'CLAIMED' WHERE user_reward_id = ?", physical);
        assertThat(RewardService.claimable(userRewardMapper.findRowById(physical).orElseThrow(), userId))
                .as("이미 신청했으면 거짓").isFalse();

        jdbc.update("UPDATE user_reward SET status = 'REJECTED' WHERE user_reward_id = ?", physical);
        assertThat(RewardService.claimable(userRewardMapper.findRowById(physical).orElseThrow(), userId))
                .as("반려된 것은 다시 신청할 수 있다").isTrue();
    }

    @Test
    @DisplayName("목록의 claimable 과 신청 판정이 어긋나지 않는다 — 결함 5-1 의 자리")
    void list_and_claim_agree() {
        physicalReward();
        long digital = digitalReward();

        List<RewardResponse> mine = rewardService.getRewards(userId);
        RewardResponse digitalRow = mine.stream()
                .filter(r -> r.userRewardId().equals(digital)).findFirst().orElseThrow();

        assertThat(digitalRow.claimable()).as("실물이 아니면 목록에서도 false").isFalse();
        assertThatThrownBy(() -> rewardService.claim(userId, digital, shipping()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REWARD_4001);
    }

    @Test
    @DisplayName("남의 보상은 신청할 수 없다")
    void cannot_claim_someone_elses_reward() {
        long physical = physicalReward();

        assertThatThrownBy(() -> rewardService.claim(otherUserId, physical, shipping()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_4032);
    }

    @Test
    @DisplayName("없는 보상은 404")
    void unknown_reward_is_404() {
        assertThatThrownBy(() -> rewardService.claim(userId, 999_999_999L, shipping()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REWARD_4040);
    }

    @Test
    @DisplayName("한 번 접수되면 다시 신청할 수 없다")
    void duplicate_claim_is_blocked() {
        long physical = physicalReward();
        assertThat(rewardService.claim(userId, physical, shipping()).claimable()).isFalse();

        assertThatThrownBy(() -> rewardService.claim(userId, physical, shipping()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REWARD_4092);
    }

    @Test
    @DisplayName("반려된 뒤에는 다시 신청할 수 있다 — 반려는 보상을 없앤 것이 아니다")
    void rejected_can_be_claimed_again() {
        long physical = physicalReward();
        rewardService.claim(userId, physical, shipping());
        rewardService.review(adminId(), physical, new ClaimReviewRequest("REJECT", "주소 확인이 필요합니다.", null));

        assertThat(status(physical)).isEqualTo("REJECTED");
        assertThat(rewardService.claim(userId, physical, shipping()).status()).isEqualTo("CLAIMED");
    }

    @Test
    @DisplayName("허용되지 않는 심사 전이는 REWARD-4090")
    void invalid_review_transition() {
        long physical = physicalReward();

        // 아직 신청하지 않은 것(GRANTED)은 심사할 수 없다.
        assertThatThrownBy(() -> rewardService.review(adminId(), physical, new ClaimReviewRequest("APPROVE", null, "1234-5678-9012")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REWARD_4090);

        rewardService.claim(userId, physical, shipping());
        rewardService.review(adminId(), physical, new ClaimReviewRequest("APPROVE", null, "1234-5678-9012"));
        assertThat(status(physical)).isEqualTo("PAID");

        // 이미 지급된 것을 또 승인할 수는 없다.
        assertThatThrownBy(() -> rewardService.review(adminId(), physical, new ClaimReviewRequest("APPROVE", null, "1234-5678-9012")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REWARD_4090);
    }

    @Test
    @DisplayName("반려에는 사유가 필요하다")
    void reject_needs_a_reason() {
        long physical = physicalReward();
        rewardService.claim(userId, physical, shipping());

        assertThatThrownBy(() -> rewardService.review(adminId(), physical, new ClaimReviewRequest("REJECT", "  ", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_4000);
    }

    @Test
    @DisplayName("조건이 깨지면 손대지 않은 것만 회수하고, 움직인 것은 사람이 보도록 표시한다")
    void cancel_revokes_only_untouched() {
        long physical = physicalReward();

        // ① 아무도 손대지 않은 것 — 그대로 회수된다.
        rewardService.revokeOrFlagByTrigger(userId, RewardPolicy.ON_ALL_COMPLETED);
        assertThat(status(physical)).as("손대지 않은 것은 회수").isEqualTo("REVOKED");

        // ② 조건이 다시 성립해 되살아난 뒤 신청까지 갔다면 — 상태를 바꾸지 않고 사람이 본다.
        rewardService.grantOrRestore(userId, RewardPolicy.ON_ALL_COMPLETED, null, pilgrimageId);
        rewardService.claim(userId, physical, shipping());
        rewardService.revokeOrFlagByTrigger(userId, RewardPolicy.ON_ALL_COMPLETED);

        assertThat(status(physical)).as("신청된 것은 상태를 바꾸지 않는다").isEqualTo("CLAIMED");
        assertThat(needsReview(physical)).as("대신 사람이 본다").isEqualTo(1);
    }

    @Test
    @DisplayName("전자일기장은 코스가 취소돼도 회수되지 않는다 — 챕터 11 결정 C")
    void diary_survives_cancellation() {
        long diary = digitalReward();

        rewardService.revokeOrFlagForPilgrimage(pilgrimageId);
        rewardService.revokeOrFlagByTrigger(userId, RewardPolicy.ON_ALL_COMPLETED);

        assertThat(status(diary)).as("이미 읽은 책을 뺏을 수는 없다").isEqualTo("GRANTED");
        assertThat(needsReview(diary)).isZero();
    }

    @Test
    @DisplayName("꺼진 정책의 과거 보상은 legacy 이고 신청할 수 없다 — 목록과 서버가 같은 이유로 막는다")
    void legacy_rewards_are_shown_but_not_claimable() {
        // 꺼진 실물 정책의 과거 행을 손으로 심는다. grant 는 is_active=1 만 보므로 이 길밖에 없다.
        Long stalePolicy = jdbc.queryForObject(
                "SELECT reward_policy_id FROM reward_policy WHERE is_active = 0 ORDER BY reward_policy_id LIMIT 1",
                Long.class);
        jdbc.update("UPDATE reward_policy SET reward_type = 'PHYSICAL' WHERE reward_policy_id = ?", stalePolicy);
        try {
            jdbc.update("""
                    INSERT INTO user_reward (user_id, reward_policy_id, pilgrimage_id, status, granted_at)
                    VALUES (?, ?, ?, 'GRANTED', NOW())
                    """, userId, stalePolicy, pilgrimageId);
            long legacy = jdbc.queryForObject(
                    "SELECT user_reward_id FROM user_reward WHERE user_id = ? AND reward_policy_id = ?",
                    Long.class, userId, stalePolicy);

            RewardResponse row = rewardService.getRewards(userId).stream()
                    .filter(r -> r.userRewardId().equals(legacy)).findFirst().orElseThrow();
            assertThat(row.legacy()).as("지난 혜택으로 표시된다").isTrue();
            assertThat(row.claimable()).as("실물이어도 신청할 수 없다").isFalse();

            assertThatThrownBy(() -> rewardService.claim(userId, legacy, shipping()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.REWARD_4001);
        } finally {
            jdbc.update("DELETE FROM user_reward WHERE user_id = ? AND reward_policy_id = ?", userId, stalePolicy);
            jdbc.update("UPDATE reward_policy SET reward_type = 'STAMP' WHERE reward_policy_id = ? AND code = 'RW-STAMP'", stalePolicy);
            jdbc.update("UPDATE reward_policy SET reward_type = 'COUPON' WHERE reward_policy_id = ? AND code = 'RW-COURSE'", stalePolicy);
        }
    }

    /* ---------------- 내부 ---------------- */

    /** 배송 정보. 이 테스트가 보는 것은 관문과 상태 전이라, 값은 통과하는 최소한으로 둔다. */
    private ClaimRequest shipping() {
        return new ClaimRequest("남산", "010-1234-5678", "서울시 중구 어딘가 1길 2", null);
    }

    /** 회향 보상(PHYSICAL) 하나를 적립하고 그 id 를 준다. */
    private long physicalReward() {
        rewardService.grant(userId, RewardPolicy.ON_ALL_COMPLETED, null, pilgrimageId);
        return jdbc.queryForObject("""
                SELECT ur.user_reward_id FROM user_reward ur
                       JOIN reward_policy rp ON rp.reward_policy_id = ur.reward_policy_id
                 WHERE ur.user_id = ? AND rp.reward_type = 'PHYSICAL'
                 ORDER BY ur.user_reward_id LIMIT 1
                """, Long.class, userId);
    }

    /**
     * 실물이 아닌 보상. 챕터 11 에서 코스 완주 쿠폰(RW-COURSE)이 꺼졌으므로
     * 그 자리를 전자일기장(DIGITAL · 3코스마다)이 잇는다.
     */
    private long digitalReward() {
        rewardService.grantOrRestore(userId, RewardPolicy.ON_EVERY_THREE_COURSES, null, null, 3);
        return jdbc.queryForObject("""
                SELECT ur.user_reward_id FROM user_reward ur
                       JOIN reward_policy rp ON rp.reward_policy_id = ur.reward_policy_id
                 WHERE ur.user_id = ? AND rp.reward_type <> 'PHYSICAL'
                 ORDER BY ur.user_reward_id LIMIT 1
                """, Long.class, userId);
    }

    private String status(long userRewardId) {
        return jdbc.queryForObject("SELECT status FROM user_reward WHERE user_reward_id = ?", String.class, userRewardId);
    }

    private int needsReview(long userRewardId) {
        return jdbc.queryForObject("SELECT needs_review FROM user_reward WHERE user_reward_id = ?", Integer.class, userRewardId);
    }

    private int rewardCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM user_reward WHERE user_id = ?", Integer.class, userId);
    }

    private long adminId() {
        return jdbc.queryForObject("SELECT user_id FROM users WHERE email = 'admin@templestamp.local'", Long.class);
    }

    private long createUser(String email, String nickname) {
        jdbc.update("""
                INSERT INTO users (email, password, nickname, role, tier, locale)
                VALUES (?, '$2a$10$FVVkQmIpuwsYytWwqTLv7exlCfmLsSNovNp13Bo6m.DrG8cLgZj1a', ?, 'USER', 'AGE30', 'ko')
                """, email, nickname);
        return jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, email);
    }

    private void cleanUp() {
        for (String email : List.of(EMAIL, OTHER_EMAIL)) {
            String in = "(SELECT user_id FROM users WHERE email = '" + email + "')";
            // 배송 정보가 user_reward 를 RESTRICT 로 잡는다 — 먼저 지운다(챕터 7 보강 B-4).
            jdbc.update("DELETE FROM reward_claim WHERE user_reward_id IN (SELECT user_reward_id FROM user_reward WHERE user_id IN " + in + ")");
            jdbc.update("DELETE FROM user_reward WHERE user_id IN " + in);
            jdbc.update("DELETE FROM certificate WHERE user_id IN " + in);
            jdbc.update("DELETE FROM ebook WHERE user_id IN " + in);
            jdbc.update("DELETE FROM stamp WHERE pilgrimage_id IN (SELECT pilgrimage_id FROM pilgrimage WHERE user_id IN " + in + ")");
            jdbc.update("DELETE FROM pilgrimage WHERE user_id IN " + in);
            jdbc.update("DELETE FROM user_agreement WHERE user_id IN " + in);
            jdbc.update("DELETE FROM users WHERE email = '" + email + "'");
        }
    }
}
