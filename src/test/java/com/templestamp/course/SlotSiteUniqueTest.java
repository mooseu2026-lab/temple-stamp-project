package com.templestamp.course;

import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 한 사찰은 같은 트랙의 한 자리에만 들어간다(챕터 11 결정 H · ALTER ㉑ · 김해원 제안).
 * <p>
 * 같은 사찰이 같은 트랙으로 두 자리에 들어가면 한 사람이 <b>같은 절에서 도장을 두 번</b> 받는 길이 열린다.
 * DB 유니크만으로는 부족하다 — {@code SlotSiteMapper.upsert} 가 {@code ON DUPLICATE KEY UPDATE} 라,
 * 다른 자리가 이미 그 (사찰, 트랙) 을 가지고 있으면 예외 대신 <b>남의 행을 조용히 고치고</b> 성공을 돌려준다.
 * 부른 쪽은 후보를 붙였다고 믿는데 행은 여전히 앞 자리의 것이다. 그래서 쓰기는
 * {@code SlotSiteService.assign} 한 곳을 지나며 주인을 먼저 확인한다.
 */
@SpringBootTest
class SlotSiteUniqueTest {

    private static final long COURSE_ID = 1L;
    private static final String PREFIX = "SLOTUK-IT-";

    @Autowired SlotSiteService slotSiteService;
    @Autowired JdbcTemplate jdbc;

    private long slot1;
    private long slot2;
    private long siteId;

    @BeforeEach
    void setUp() {
        cleanUp();
        slot1 = jdbc.queryForObject(
                "SELECT course_site_id FROM course_site WHERE course_id = ? AND position = 1", Long.class, COURSE_ID);
        slot2 = jdbc.queryForObject(
                "SELECT course_site_id FROM course_site WHERE course_id = ? AND position = 2", Long.class, COURSE_ID);

        jdbc.update("""
                INSERT INTO site (name, latitude, longitude, verify_radius, qr_location_hint, status)
                VALUES (?, 37.63, 127.04, 200, '일주문 옆', 'ACTIVE')""", PREFIX + "후보사찰");
        siteId = jdbc.queryForObject("SELECT site_id FROM site WHERE name = ?", Long.class, PREFIX + "후보사찰");
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("같은 자리에 몇 번을 붙여도 한 행이다 — 시더 재실행이 멱등한 이유")
    void assigning_the_same_slot_twice_is_idempotent() {
        slotSiteService.assign(candidate(slot1, "MAIN", 1));
        slotSiteService.assign(candidate(slot1, "MAIN", 2));

        assertThat(rowCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT sort_no FROM slot_site WHERE site_id = ? AND track = 'MAIN'", Integer.class, siteId))
                .as("부가 정보는 갱신된다").isEqualTo(2);
    }

    @Test
    @DisplayName("같은 트랙의 다른 자리에 넣으려 하면 409 COURSE-4093 이다")
    void the_same_site_cannot_join_another_slot_on_the_same_track() {
        slotSiteService.assign(candidate(slot1, "MAIN", 1));

        assertThatThrownBy(() -> slotSiteService.assign(candidate(slot2, "MAIN", 1)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COURSE_4093);

        assertThat(rowCount()).as("막혔으니 행은 그대로 하나").isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT course_site_id FROM slot_site WHERE site_id = ? AND track = 'MAIN'", Long.class, siteId))
                .as("남의 행이 조용히 옮겨 가지 않았다").isEqualTo(slot1);
    }

    @Test
    @DisplayName("트랙이 다르면 다른 자리에도 들어갈 수 있다 — 유니크는 (사찰, 트랙) 이다")
    void a_different_track_is_allowed() {
        slotSiteService.assign(candidate(slot1, "MAIN", 1));
        slotSiteService.assign(candidate(slot2, "SUNROAD", 1));

        assertThat(rowCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("서비스를 건너뛰고 직접 넣으면 DB 가 막는다 — 1062")
    void the_database_is_the_last_line() {
        slotSiteService.assign(candidate(slot1, "MAIN", 1));

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO slot_site (course_site_id, site_id, track, sort_no)
                VALUES (?, ?, 'MAIN', 9)""", slot2, siteId))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("지금 데이터에 위반이 없다 — ALTER ㉑ 을 넣기 전 사전 검사와 같은 질의")
    void existing_data_has_no_violation() {
        assertThat(jdbc.queryForList("""
                SELECT site_id FROM slot_site GROUP BY site_id, track HAVING COUNT(*) > 1
                """, Long.class)).isEmpty();
    }

    /* ---------------- 내부 ---------------- */

    private SlotSite candidate(long courseSiteId, String track, int sortNo) {
        SlotSite s = new SlotSite();
        s.setCourseSiteId(courseSiteId);
        s.setSiteId(siteId);
        s.setTrack(track);
        s.setSortNo(sortNo);
        s.setIsStar(false);   // NOT NULL 이다 — 시더도 이 값을 반드시 채운다
        return s;
    }

    private int rowCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM slot_site WHERE site_id = ?", Integer.class, siteId);
    }

    private void cleanUp() {
        jdbc.update("DELETE FROM slot_site WHERE site_id IN (SELECT site_id FROM site WHERE name LIKE ?)",
                PREFIX + "%");
        jdbc.update("DELETE FROM site WHERE name LIKE ?", PREFIX + "%");
    }
}
