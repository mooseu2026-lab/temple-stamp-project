package com.templestamp.ebook;

import com.templestamp.ebook.dto.EbookResponse;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
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
 * 전자일기장(챕터 11 결정 B · 항목 6).
 * <p>
 * 두 가지가 이 챕터에서 바뀌었다.
 * <ul>
 *   <li><b>도장이 없어도 만들어진다.</b> "재료가 없다" 의 기준이 도장 수에서 <b>전체 기록</b>으로 바뀌었다 —
 *       절에 가서 사진만 찍고 생각상자만 쓴 사람도 자기 책을 가질 수 있어야 한다.</li>
 *   <li><b>마일스톤이 정체성이다.</b> {@code (사용자, 종류, 마일스톤)} 유니크가 없으면
 *       6코스 책이 3코스 책과 같은 것으로 취급돼 만들어지지 않는다.</li>
 * </ul>
 */
@SpringBootTest
class EbookInterimTest {

    private static final String EMAIL = "ebook-interim@test.com";
    private static final long COURSE_ID = 1L;

    @Autowired EbookService ebookService;
    @Autowired JdbcTemplate jdbc;

    private long userId;
    private long siteId;

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbc.update("""
                INSERT INTO users (email, password, nickname, role, tier, locale)
                VALUES (?, '$2a$10$FVVkQmIpuwsYytWwqTLv7exlCfmLsSNovNp13Bo6m.DrG8cLgZj1a', '일기시험', 'USER', 'AGE30', 'ko')
                """, EMAIL);
        userId = jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, EMAIL);
        siteId = jdbc.queryForObject(
                "SELECT site_id FROM course_site WHERE course_id = ? AND position = 1", Long.class, COURSE_ID);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("기록이 하나도 없으면 400 — 표지와 판권만 남은 책은 내보내지 않는다")
    void nothing_to_bind_is_a_400() {
        assertThat(ebookService.materialsOf(userId).isEmpty()).isTrue();

        assertThatThrownBy(() -> ebookService.request(userId, Ebook.PERSONAL))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EBOOK_4001);
    }

    @Test
    @DisplayName("도장이 하나도 없어도 사진·생각상자만 있으면 만들어진다 — 옛 기준은 이 사람을 막았다")
    void photos_and_thinkbox_are_enough() {
        addPhoto(false);
        addThinkbox();

        assertThat(ebookService.materialsOf(userId).stampCount()).as("전제: 도장 0").isZero();
        assertThat(ebookService.materialsOf(userId).photos()).hasSize(1);

        EbookResponse created = ebookService.request(userId, Ebook.PERSONAL);
        assertThat(created.status()).isEqualTo(Ebook.REQUESTED);
    }

    @Test
    @DisplayName("타인 얼굴 사진은 재료에 들어가지 않는다 — 그것만 있으면 여전히 400")
    void other_face_photos_are_excluded() {
        addPhoto(true);

        assertThat(ebookService.materialsOf(userId).photos()).isEmpty();
        assertThatThrownBy(() -> ebookService.request(userId, Ebook.PERSONAL))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("3코스에 못 미치면 INTERIM 을 달라 해도 개인 소장본으로 만든다 — 거절하지 않는다")
    void below_three_courses_falls_back_to_personal() {
        addThinkbox();
        completedCourses(2);

        EbookResponse created = ebookService.request(userId, Ebook.INTERIM);

        assertThat(typeOf(created)).isEqualTo(Ebook.PERSONAL);
        assertThat(milestoneOf(created)).isNull();
    }

    @Test
    @DisplayName("4코스면 마일스톤은 3이다 — 완주 수를 3으로 내림한다")
    void milestone_is_floored_to_three() {
        addThinkbox();
        completedCourses(4);

        EbookResponse created = ebookService.request(userId, Ebook.INTERIM);

        assertThat(typeOf(created)).isEqualTo(Ebook.INTERIM);
        assertThat(milestoneOf(created)).isEqualTo(3);
    }

    @Test
    @DisplayName("같은 마일스톤을 몇 번 요청해도 한 권이고, 다음 마일스톤은 새 책이다")
    void milestone_is_idempotent_and_the_next_one_is_a_new_book() {
        addThinkbox();
        completedCourses(3);

        EbookResponse first = ebookService.request(userId, Ebook.INTERIM);
        EbookResponse again = ebookService.request(userId, Ebook.INTERIM);
        assertThat(again.ebookId()).as("같은 책을 돌려준다").isEqualTo(first.ebookId());
        assertThat(interimMilestones()).containsExactly(3);

        completedCourses(6);
        EbookResponse second = ebookService.request(userId, Ebook.INTERIM);
        assertThat(second.ebookId()).as("6코스는 다른 책이다").isNotEqualTo(first.ebookId());
        assertThat(interimMilestones()).containsExactly(3, 6);
    }

    /* ---------------- 내부 ---------------- */

    private void addPhoto(boolean otherFace) {
        jdbc.update("""
                INSERT INTO photo (user_id, site_id, file_key, has_other_face, is_private)
                VALUES (?, ?, ?, ?, 1)
                """, userId, siteId, "PHOTO/" + userId + "/00000000-0000-0000-0000-000000000001.jpg",
                otherFace ? 1 : 0);
    }

    private void addThinkbox() {
        jdbc.update("""
                INSERT INTO thinkbox (user_id, site_id, body, source, is_private)
                VALUES (?, ?, '오늘 걸으며 떠오른 것을 적어 둔다.', 'DIRECT', 1)
                """, userId, siteId);
    }

    /** 완주 코스를 정확히 n 개로 맞춘다. 마일스톤의 근거는 이 숫자 하나다. */
    private void completedCourses(int n) {
        jdbc.update("DELETE FROM pilgrimage WHERE user_id = ?", userId);
        jdbc.update("""
                INSERT INTO pilgrimage (user_id, course_id, status, completed_at)
                SELECT ?, course_id, 'COMPLETED', NOW() FROM course ORDER BY course_id LIMIT ?
                """, userId, n);
    }

    private String typeOf(EbookResponse response) {
        return jdbc.queryForObject("SELECT ebook_type FROM ebook WHERE ebook_id = ?",
                String.class, response.ebookId());
    }

    private Integer milestoneOf(EbookResponse response) {
        return jdbc.queryForObject("SELECT milestone FROM ebook WHERE ebook_id = ?",
                Integer.class, response.ebookId());
    }

    private List<Integer> interimMilestones() {
        return jdbc.queryForList(
                "SELECT milestone FROM ebook WHERE user_id = ? AND ebook_type = 'INTERIM' ORDER BY milestone",
                Integer.class, userId);
    }

    private void cleanUp() {
        String in = "(SELECT user_id FROM users WHERE email = '" + EMAIL + "')";
        jdbc.update("DELETE FROM print_order WHERE ebook_id IN (SELECT ebook_id FROM ebook WHERE user_id IN " + in + ")");
        jdbc.update("DELETE FROM ebook WHERE user_id IN " + in);
        jdbc.update("DELETE FROM thinkbox WHERE user_id IN " + in);
        jdbc.update("DELETE FROM photo WHERE user_id IN " + in);
        jdbc.update("DELETE FROM certificate WHERE user_id IN " + in);
        jdbc.update("DELETE FROM reward_claim WHERE user_reward_id IN (SELECT user_reward_id FROM user_reward WHERE user_id IN " + in + ")");
        jdbc.update("DELETE FROM user_reward WHERE user_id IN " + in);
        jdbc.update("DELETE FROM stamp WHERE pilgrimage_id IN (SELECT pilgrimage_id FROM pilgrimage WHERE user_id IN " + in + ")");
        jdbc.update("DELETE FROM pilgrimage WHERE user_id IN " + in);
        jdbc.update("DELETE FROM user_agreement WHERE user_id IN " + in);
        jdbc.update("DELETE FROM users WHERE email = '" + EMAIL + "'");
    }
}
