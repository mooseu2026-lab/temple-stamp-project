package com.templestamp.reward;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 사용자 단위 보상의 유니크(챕터 11 리뷰 2-2 · ALTER ⑳).
 * <p>
 * "이 사람의 누적" 에 주는 보상 — 3코스마다의 전자일기장, 12코스 완주의 특별앨범 — 은
 * 지금까지 <b>방금 끝낸 코스의 id</b> 로 저장됐다. 그래서 코스 하나를 반려했다가 재승인하면
 * 다른 코스 id 로 한 행이 더 생겼다. 실물 기념품이라면 두 개가 나간다.
 * <p>
 * 리뷰의 시나리오를 그대로 옮긴다 — 12코스 완주 → 취소 → 재승인. 끝난 뒤 (정책, 사용자) 로 한 행이어야 한다.
 * ACTIVE 코스가 1개인 지금은 이 길에 도달할 수 없지만, 12코스를 열기 전에 닫아야 하는 문이라
 * 서비스 호출로 그 순서를 직접 재현한다.
 */
@SpringBootTest
class RewardUserScopeTest {

    private static final String EMAIL = "reward-scope@test.com";
    private static final long COURSE_ID = 1L;

    @Autowired RewardService rewardService;
    @Autowired JdbcTemplate jdbc;

    private long userId;
    private long pilgrimageA;
    private long pilgrimageB;

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbc.update("""
                INSERT INTO users (email, password, nickname, role, tier, locale)
                VALUES (?, '$2a$10$FVVkQmIpuwsYytWwqTLv7exlCfmLsSNovNp13Bo6m.DrG8cLgZj1a', '범위시험', 'USER', 'AGE30', 'ko')
                """, EMAIL);
        userId = jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, EMAIL);
        pilgrimageA = newPilgrimage(COURSE_ID);
        pilgrimageB = newPilgrimage(jdbc.queryForObject(
                "SELECT MIN(course_id) FROM course WHERE course_id <> ?", Long.class, COURSE_ID));
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("사용자 단위 보상은 코스 id 를 담지 않는다 — 담으면 우연이 키가 된다")
    void user_scoped_reward_has_no_pilgrimage_id() {
        rewardService.grantOrRestore(userId, RewardPolicy.ON_ALL_COMPLETED, null, pilgrimageA);

        assertThat(rows(RewardPolicy.ON_ALL_COMPLETED)).hasSize(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_reward ur JOIN reward_policy rp
                       ON rp.reward_policy_id = ur.reward_policy_id
                 WHERE ur.user_id = ? AND rp.trigger_type = 'ALL_COMPLETED'
                   AND ur.pilgrimage_id IS NULL AND ur.stamp_id IS NULL
                """, Integer.class, userId)).isEqualTo(1);
    }

    @Test
    @DisplayName("취소 → 다른 코스로 재승인해도 특별앨범은 한 행이다 — 리뷰 2-2 시나리오")
    void revoke_then_regrant_with_another_course_keeps_one_row() {
        rewardService.grantOrRestore(userId, RewardPolicy.ON_ALL_COMPLETED, null, pilgrimageA);
        rewardService.revokeOrFlagByTrigger(userId, RewardPolicy.ON_ALL_COMPLETED);

        // 재승인. 이번에는 "마지막으로 끝난 코스" 가 B 다 — 옛 저장 방식이라면 여기서 두 번째 행이 생겼다.
        rewardService.grantOrRestore(userId, RewardPolicy.ON_ALL_COMPLETED, null, pilgrimageB);

        List<String> statuses = rows(RewardPolicy.ON_ALL_COMPLETED);
        assertThat(statuses).as("행이 늘지 않는다").hasSize(1);
        assertThat(statuses.get(0)).as("같은 행이 되살아난다").isEqualTo("GRANTED");
    }

    @Test
    @DisplayName("전자일기장은 마일스톤마다 한 행이다 — 3과 6은 다른 책이다")
    void diary_is_one_row_per_milestone() {
        rewardService.grantOrRestore(userId, RewardPolicy.ON_EVERY_THREE_COURSES, null, null, 3);
        rewardService.grantOrRestore(userId, RewardPolicy.ON_EVERY_THREE_COURSES, null, null, 3);
        assertThat(milestones()).as("같은 마일스톤은 두 번 적립되지 않는다").containsExactly(3);

        rewardService.grantOrRestore(userId, RewardPolicy.ON_EVERY_THREE_COURSES, null, null, 6);
        assertThat(milestones()).as("다음 마일스톤은 새 행이다").containsExactly(3, 6);
    }

    @Test
    @DisplayName("억지로 같은 행을 한 번 더 넣으면 DB 가 막는다 — 1062")
    void duplicate_insert_is_rejected_by_the_unique_key() {
        rewardService.grantOrRestore(userId, RewardPolicy.ON_EVERY_THREE_COURSES, null, null, 3);
        Long policyId = jdbc.queryForObject(
                "SELECT reward_policy_id FROM reward_policy WHERE trigger_type = 'EVERY_THREE_COURSES'", Long.class);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO user_reward (user_id, reward_policy_id, milestone, status, granted_at)
                VALUES (?, ?, 3, 'GRANTED', NOW())
                """, userId, policyId))
                .isInstanceOf(DuplicateKeyException.class);
    }

    /* ---------------- 내부 ---------------- */

    private long newPilgrimage(long courseId) {
        jdbc.update("INSERT INTO pilgrimage (user_id, course_id, status, completed_at) VALUES (?, ?, 'COMPLETED', NOW())",
                userId, courseId);
        return jdbc.queryForObject(
                "SELECT pilgrimage_id FROM pilgrimage WHERE user_id = ? AND course_id = ?",
                Long.class, userId, courseId);
    }

    private List<String> rows(String triggerType) {
        return jdbc.queryForList("""
                SELECT ur.status FROM user_reward ur JOIN reward_policy rp
                       ON rp.reward_policy_id = ur.reward_policy_id
                 WHERE ur.user_id = ? AND rp.trigger_type = ?
                 ORDER BY ur.user_reward_id
                """, String.class, userId, triggerType);
    }

    private List<Integer> milestones() {
        return jdbc.queryForList("""
                SELECT ur.milestone FROM user_reward ur JOIN reward_policy rp
                       ON rp.reward_policy_id = ur.reward_policy_id
                 WHERE ur.user_id = ? AND rp.trigger_type = 'EVERY_THREE_COURSES'
                 ORDER BY ur.milestone
                """, Integer.class, userId);
    }

    private void cleanUp() {
        String in = "(SELECT user_id FROM users WHERE email = '" + EMAIL + "')";
        jdbc.update("DELETE FROM reward_claim WHERE user_reward_id IN (SELECT user_reward_id FROM user_reward WHERE user_id IN " + in + ")");
        jdbc.update("DELETE FROM user_reward WHERE user_id IN " + in);
        jdbc.update("DELETE FROM certificate WHERE user_id IN " + in);
        jdbc.update("DELETE FROM ebook WHERE user_id IN " + in);
        jdbc.update("DELETE FROM pilgrimage WHERE user_id IN " + in);
        jdbc.update("DELETE FROM user_agreement WHERE user_id IN " + in);
        jdbc.update("DELETE FROM users WHERE email = '" + EMAIL + "'");
    }
}
