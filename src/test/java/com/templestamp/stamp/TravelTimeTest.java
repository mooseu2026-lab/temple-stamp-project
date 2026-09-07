package com.templestamp.stamp;

import com.templestamp.global.config.StampProperties;
import com.templestamp.stamp.dto.MissionResultResponse;
import com.templestamp.stamp.dto.MissionSubmitRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이동시간 검사(챕터 11 결정 D·E).
 * <p>
 * 세 가지를 고정한다.
 * <ul>
 *   <li><b>비교 대상은 실제로 인증한 사찰</b>이다 — 자리의 대표 사찰이 아니다(리뷰 1-2).
 *       후보로 찍은 사람의 이동시간이 엉뚱한 두 점 사이로 계산되던 결함이다.</li>
 *   <li><b>범위는 사람</b>이다 — 코스를 갈아타며 순간이동하는 길을 막는다(결정 E).</li>
 *   <li><b>표에 없는 쌍도 검사한다</b> — 기본 15분. 이 값이 0이면 검사가 통째로 꺼진다(결정 D).</li>
 * </ul>
 * HTTP 를 거치지 않는다. 여기서 볼 것은 "무엇과 무엇을 비교하는가" 라서,
 * 요청으로 만들면 하루 한도·세션 만료 같은 다른 규칙에 먼저 걸려 무엇을 보고 있는지 흐려진다.
 */
@SpringBootTest
class TravelTimeTest {

    private static final long COURSE_ID = 1L;
    private static final String EMAIL = "travel-test@test.com";
    private static final String PREFIX = "TRAVEL-IT-";
    private static final String SENTENCE = "오늘 받은 것들의 이름을 하나씩 불러본다.";

    @Autowired StampService stampService;
    @Autowired StampMapper stampMapper;
    @Autowired StampProperties stampProperties;
    @Autowired JdbcTemplate jdbc;

    private long userId;
    private long pilgrimageA;      // 코스 1
    private long pilgrimageB;      // 다른 코스 — 사용자 단위인지 보는 데 쓴다
    private long otherCourseId;
    private final long[] slot = new long[6];
    private final long[] site = new long[6];
    private long candidate;        // 자리 1의 대표가 아닌 후보 사찰

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbc.update("""
                INSERT INTO users (email, password, nickname, role, tier, locale)
                VALUES (?, '$2a$10$FVVkQmIpuwsYytWwqTLv7exlCfmLsSNovNp13Bo6m.DrG8cLgZj1a', '이동시험', 'USER', 'AGE30', 'ko')
                """, EMAIL);
        userId = jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, EMAIL);

        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT course_site_id, site_id, position FROM course_site WHERE course_id = ? ORDER BY position",
                COURSE_ID)) {
            int p = ((Number) row.get("position")).intValue();
            slot[p] = ((Number) row.get("course_site_id")).longValue();
            site[p] = ((Number) row.get("site_id")).longValue();
        }

        // 자리 1의 후보 사찰. 대표와 다른 사찰이라 "무엇과 비교하는가" 가 눈에 보인다.
        jdbc.update("""
                INSERT INTO site (name, latitude, longitude, verify_radius, qr_location_hint, status)
                VALUES (?, 37.62, 127.03, 200, '일주문 옆', 'ACTIVE')""", PREFIX + "후보사찰");
        candidate = jdbc.queryForObject("SELECT site_id FROM site WHERE name = ?", Long.class, PREFIX + "후보사찰");
        jdbc.update("""
                INSERT INTO slot_site (course_site_id, site_id, track, sort_no)
                VALUES (?, ?, 'SUNROAD', 1)""", slot[1], candidate);

        pilgrimageA = newPilgrimage(COURSE_ID);
        otherCourseId = jdbc.queryForObject(
                "SELECT MIN(course_id) FROM course_site WHERE course_id <> ?", Long.class, COURSE_ID);
        pilgrimageB = newPilgrimage(otherCourseId);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("설정 기본값은 15분이다 — 0이면 검사가 통째로 꺼진다")
    void default_is_fifteen_minutes() {
        assertThat(stampProperties.defaultTravelMinutes())
                .as("0이면 site_distance 에 없는 모든 쌍이 무검사로 통과한다")
                .isEqualTo(15);
    }

    @Test
    @DisplayName("직전 사찰은 자리의 대표가 아니라 실제로 인증한 후보다")
    void last_site_is_the_one_actually_verified() {
        completedStamp(pilgrimageA, slot[1], candidate, 5);

        assertThat(candidate).as("전제: 대표와 다른 사찰이다").isNotEqualTo(site[1]);
        assertThat(stampMapper.findLastCompletedSiteId(userId)).contains(candidate);
    }

    @Test
    @DisplayName("옛 행처럼 site_id 가 비어 있으면 자리의 대표로 받는다 — COALESCE")
    void old_rows_fall_back_to_the_representative() {
        long stampId = completedStamp(pilgrimageA, slot[1], candidate, 5);
        jdbc.update("UPDATE stamp SET site_id = NULL WHERE stamp_id = ?", stampId);

        assertThat(stampMapper.findLastCompletedSiteId(userId)).contains(site[1]);
    }

    @Test
    @DisplayName("범위는 코스가 아니라 사람이다 — 다른 코스에서 찍은 도장도 직전으로 잡힌다")
    void scope_is_the_user_not_the_course() {
        completedStamp(pilgrimageB, otherSlot(), otherSite(), 5);

        assertThat(stampMapper.findLastCompletedSiteId(userId))
                .as("코스 A 에는 완료 도장이 하나도 없지만, 이 사람에게는 있다").isPresent();
        assertThat(stampMapper.findMinutesSinceLastCompleted(userId)).contains(5);
    }

    @Test
    @DisplayName("표에 없는 쌍도 기본 15분으로 걸린다 — 코스를 갈아타도 마찬가지")
    void unknown_pair_uses_the_default() {
        completedStamp(pilgrimageB, otherSlot(), otherSite(), 5);   // 5분 전, 다른 코스
        long stampId = qrDoneStamp(pilgrimageA, slot[2], site[2]);

        MissionResultResponse result = stampService.submitMission(
                userId, stampId, new MissionSubmitRequest(SENTENCE, null, null, null));

        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject(
                "SELECT pending_reason FROM stamp WHERE stamp_id = ?", String.class, stampId))
                .isEqualTo("TRAVEL_TIME");
    }

    @Test
    @DisplayName("사이 거리 표가 있으면 표 값이 기본값을 이긴다")
    void the_table_wins_over_the_default() {
        long from = otherSite();
        completedStamp(pilgrimageB, otherSlot(), from, 5);
        long stampId = qrDoneStamp(pilgrimageA, slot[2], site[2]);
        // 2분이면 충분한 거리라고 표에 적어 둔다 — 기본 15분이었다면 걸렸을 상황이다.
        // 시드 사찰끼리의 행이라 이름으로 지울 수 없다. 이 검사가 넣은 것은 이 검사가 치운다.
        jdbc.update("""
                INSERT INTO site_distance (site_a_id, site_b_id, min_minutes) VALUES (?, ?, 2)
                ON DUPLICATE KEY UPDATE min_minutes = 2""", from, site[2]);
        try {
            MissionResultResponse result = stampService.submitMission(
                    userId, stampId, new MissionSubmitRequest(SENTENCE, null, null, null));

            assertThat(result.status()).isNotEqualTo("PENDING");
        } finally {
            jdbc.update("DELETE FROM site_distance WHERE site_a_id = ? AND site_b_id = ?", from, site[2]);
        }
    }

    /* ---------------- 내부 ---------------- */

    private long newPilgrimage(long courseId) {
        jdbc.update("INSERT INTO pilgrimage (user_id, course_id, status) VALUES (?, ?, 'IN_PROGRESS')",
                userId, courseId);
        return jdbc.queryForObject(
                "SELECT pilgrimage_id FROM pilgrimage WHERE user_id = ? AND course_id = ?",
                Long.class, userId, courseId);
    }

    private long otherSlot() {
        return jdbc.queryForObject(
                "SELECT MIN(course_site_id) FROM course_site WHERE course_id = ?", Long.class, otherCourseId);
    }

    private long otherSite() {
        return jdbc.queryForObject(
                "SELECT site_id FROM course_site WHERE course_site_id = ?", Long.class, otherSlot());
    }

    /** 완료 도장 하나를 n분 전에 만든다. */
    private long completedStamp(long pilgrimageId, long courseSiteId, long siteId, int minutesAgo) {
        jdbc.update("""
                INSERT INTO stamp (pilgrimage_id, course_site_id, site_id, verify_status,
                                   verify_method, user_sentence, gps_verified_at, mission_verified_at)
                VALUES (?, ?, ?, 'COMPLETED', 'GPS_QR', ?,
                        DATE_SUB(NOW(), INTERVAL ? MINUTE), DATE_SUB(NOW(), INTERVAL ? MINUTE))
                """, pilgrimageId, courseSiteId, siteId, SENTENCE, minutesAgo, minutesAgo);
        return jdbc.queryForObject("SELECT MAX(stamp_id) FROM stamp WHERE pilgrimage_id = ?",
                Long.class, pilgrimageId);
    }

    /** 3단계 직전(QR 까지 마친) 도장. 세션은 방금 시작한 것으로 둔다. */
    private long qrDoneStamp(long pilgrimageId, long courseSiteId, long siteId) {
        jdbc.update("""
                INSERT INTO stamp (pilgrimage_id, course_site_id, site_id, verify_status,
                                   verify_method, gps_verified_at, qr_verified_at)
                VALUES (?, ?, ?, 'QR_DONE', 'GPS_QR', NOW(), NOW())
                """, pilgrimageId, courseSiteId, siteId);
        return jdbc.queryForObject("SELECT MAX(stamp_id) FROM stamp WHERE pilgrimage_id = ?",
                Long.class, pilgrimageId);
    }

    private void cleanUp() {
        String in = "(SELECT user_id FROM users WHERE email = '" + EMAIL + "')";
        jdbc.update("DELETE FROM reward_claim WHERE user_reward_id IN (SELECT user_reward_id FROM user_reward WHERE user_id IN " + in + ")");
        jdbc.update("DELETE FROM user_reward WHERE user_id IN " + in);
        jdbc.update("DELETE FROM certificate WHERE user_id IN " + in);
        jdbc.update("DELETE FROM ebook WHERE user_id IN " + in);
        jdbc.update("DELETE FROM thinkbox WHERE user_id IN " + in);
        jdbc.update("DELETE FROM photo WHERE user_id IN " + in);
        jdbc.update("DELETE FROM stamp WHERE pilgrimage_id IN (SELECT pilgrimage_id FROM pilgrimage WHERE user_id IN " + in + ")");
        jdbc.update("DELETE FROM phrase_seen WHERE user_id IN " + in);
        jdbc.update("DELETE FROM task_seen WHERE user_id IN " + in);
        jdbc.update("DELETE FROM pilgrimage WHERE user_id IN " + in);
        jdbc.update("DELETE FROM user_agreement WHERE user_id IN " + in);
        jdbc.update("DELETE FROM users WHERE email = '" + EMAIL + "'");
        // 사이 거리·후보는 사찰보다 먼저 지운다 — FK 가 둘 다 RESTRICT 다.
        jdbc.update("DELETE FROM site_distance WHERE site_a_id IN (SELECT site_id FROM site WHERE name LIKE ?)",
                PREFIX + "%");
        jdbc.update("DELETE FROM site_distance WHERE site_b_id IN (SELECT site_id FROM site WHERE name LIKE ?)",
                PREFIX + "%");
        jdbc.update("DELETE FROM slot_site WHERE site_id IN (SELECT site_id FROM site WHERE name LIKE ?)",
                PREFIX + "%");
        jdbc.update("DELETE FROM site WHERE name LIKE ?", PREFIX + "%");
    }
}
