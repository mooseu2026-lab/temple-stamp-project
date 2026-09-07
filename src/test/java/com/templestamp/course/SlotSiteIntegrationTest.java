package com.templestamp.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * v4 슬롯 후보 — 한 자리(구)에서 인증할 수 있는 사찰이 여럿이다.
 * <p>
 * 챕터 5 의 GPS 인증이 {@code slotSiteMapper.exists(courseSiteId, siteId)} 하나로 "이 자리의 후보인가" 를
 * 판단하므로, 그 질의가 track 을 가리지 않는다는 것과 코스 상세가 후보를 실어 보낸다는 것을 여기서 고정한다.
 * <p>
 * 기존 코스 1(서울 도심 다섯 절, ACTIVE)의 1번 자리에 후보를 붙였다가 지운다.
 * {@code slot_site} 의 FK 는 둘 다 RESTRICT 라 사찰보다 후보를 먼저 지워야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SlotSiteIntegrationTest {

    private static final long COURSE_ID = 1L;
    private static final String PREFIX = "SLOT-IT-";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;
    @Autowired SlotSiteMapper slotSiteMapper;

    private long slotId;          // course_site_id (1번 자리)
    private long representative;  // 그 자리의 대표 사찰
    private long alternative;     // 이번에 만든 대체 후보

    @BeforeEach
    void setUp() {
        cleanUp();
        slotId = jdbc.queryForObject(
                "SELECT course_site_id FROM course_site WHERE course_id = ? AND position = 1", Long.class, COURSE_ID);
        representative = jdbc.queryForObject(
                "SELECT site_id FROM course_site WHERE course_site_id = ?", Long.class, slotId);

        jdbc.update("""
                INSERT INTO site (name, latitude, longitude, verify_radius, qr_location_hint, status)
                VALUES (?, 37.60, 127.01, 200, '일주문 옆', 'ACTIVE')""", PREFIX + "대체사찰");
        alternative = jdbc.queryForObject("SELECT site_id FROM site WHERE name = ?", Long.class, PREFIX + "대체사찰");

        // 대표는 MAIN sort 1, 대체는 SUNROAD sort 1 — 라이더 정렬을 볼 수 있게 track 을 나눈다
        jdbc.update("""
                INSERT INTO slot_site (course_site_id, site_id, track, sort_no, serving_note)
                VALUES (?, ?, 'MAIN', 1, '대표')""", slotId, representative);
        jdbc.update("""
                INSERT INTO slot_site (course_site_id, site_id, track, sort_no, route_note, is_star)
                VALUES (?, ?, 'SUNROAD', 1, '북한산 능선', 1)""", slotId, alternative);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("exists 는 track 을 가리지 않는다 — 선로드 후보에서도 도장을 찍을 수 있어야 한다")
    void exists_ignores_track() {
        assertThat(slotSiteMapper.exists(slotId, representative)).isTrue();
        assertThat(slotSiteMapper.exists(slotId, alternative)).isTrue();
        assertThat(slotSiteMapper.exists(slotId, 999_999L)).isFalse();   // 이 자리의 후보가 아닌 사찰
    }

    @Test
    @DisplayName("코스 상세의 자리마다 candidates 가 실린다 — 대표도 목록에 들어간다")
    void course_detail_carries_candidates() throws Exception {
        JsonNode slot = firstSlot(null);

        List<Long> ids = siteIds(slot);
        assertThat(ids).containsExactlyInAnyOrder(representative, alternative);
        assertThat(slot.path("siteId").asLong()).isEqualTo(representative);   // 대표는 자리 자체의 siteId 로도 온다

        JsonNode sunroad = candidateOf(slot, alternative);
        assertThat(sunroad.path("track").asText()).isEqualTo("SUNROAD");
        assertThat(sunroad.path("routeNote").asText()).isEqualTo("북한산 능선");
        assertThat(sunroad.path("isStar").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("target=RIDER 면 SUNROAD 가 앞으로 온다 — 감추는 것이 아니라 순서만 바뀐다")
    void rider_sees_sunroad_first() throws Exception {
        assertThat(siteIds(firstSlot(null))).startsWith(representative);
        assertThat(siteIds(firstSlot("RIDER"))).startsWith(alternative);
        assertThat(siteIds(firstSlot("RIDER"))).hasSize(2);
    }

    @Test
    @DisplayName("과포화 후보는 목록에 남고 congested=true 로 표시된다 — 빼지 않는다")
    void congested_candidate_stays_and_is_flagged() throws Exception {
        jdbc.update("UPDATE slot_site SET is_congested = 1 WHERE site_id = ?", alternative);

        JsonNode slot = firstSlot(null);
        assertThat(siteIds(slot)).containsExactlyInAnyOrder(representative, alternative);
        assertThat(candidateOf(slot, alternative).path("congested").asBoolean()).isTrue();
        assertThat(candidateOf(slot, representative).path("congested").asBoolean()).isFalse();
        assertThat(slotSiteMapper.exists(slotId, alternative)).isTrue();   // 인증도 그대로 된다
    }

    @Test
    @DisplayName("과포화는 같은 track 안에서 뒤로 밀린다 — track 이 1차, congested 가 2차 기준")
    void congested_sinks_within_its_track() throws Exception {
        // 같은 MAIN 에 후보를 하나 더 둔다. 대표(sort 1)를 과포화로 만들면 sort 2 가 앞선다.
        jdbc.update("""
                INSERT INTO site (name, latitude, longitude, verify_radius, qr_location_hint, status)
                VALUES (?, 37.61, 127.02, 200, '천왕문 앞', 'ACTIVE')""", PREFIX + "두번째MAIN");
        long secondMain = jdbc.queryForObject("SELECT site_id FROM site WHERE name = ?", Long.class, PREFIX + "두번째MAIN");
        jdbc.update("""
                INSERT INTO slot_site (course_site_id, site_id, track, sort_no)
                VALUES (?, ?, 'MAIN', 2)""", slotId, secondMain);

        assertThat(siteIds(firstSlot(null))).containsExactly(representative, secondMain, alternative);

        jdbc.update("UPDATE slot_site SET is_congested = 1 WHERE site_id = ? AND course_site_id = ?",
                representative, slotId);
        assertThat(siteIds(firstSlot(null))).containsExactly(secondMain, representative, alternative);
    }

    @Test
    @DisplayName("track 이 congested 보다 먼저다 — RIDER 는 혼잡한 SUNROAD 도 맨 앞에 본다")
    void track_outranks_congested() throws Exception {
        jdbc.update("UPDATE slot_site SET is_congested = 1 WHERE site_id = ?", alternative);

        // 라이더가 고르는 기준은 "길" 이다. 혼잡 여부는 배지로 알리고 순서를 뒤집지는 않는다.
        assertThat(siteIds(firstSlot("RIDER"))).containsExactly(alternative, representative);
        JsonNode slot = firstSlot("RIDER");
        assertThat(candidateOf(slot, alternative).path("congested").asBoolean()).isTrue();
    }

    /* ---------------- 내부 ---------------- */

    private JsonNode firstSlot(String target) throws Exception {
        String url = "/api/courses/" + COURSE_ID + (target == null ? "" : "?target=" + target);
        String body = mvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode site : om.readTree(body).path("data").path("sites")) {
            if (site.path("courseSiteId").asLong() == slotId) {
                return site;
            }
        }
        throw new AssertionError("코스 상세에 1번 자리가 없다");
    }

    private static List<Long> siteIds(JsonNode slot) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode c : slot.path("candidates")) {
            ids.add(c.path("siteId").asLong());
        }
        return ids;
    }

    private static JsonNode candidateOf(JsonNode slot, long siteId) {
        for (JsonNode c : slot.path("candidates")) {
            if (c.path("siteId").asLong() == siteId) {
                return c;
            }
        }
        throw new AssertionError("candidates 에 siteId=" + siteId + " 가 없다");
    }

    private void cleanUp() {
        // 코스 1(데모)의 자리에는 시드 후보가 붙지 않는다 — 시드 코스는 "○○ 공양의 길" 12개뿐이다.
        jdbc.update("""
                DELETE FROM slot_site
                 WHERE course_site_id IN (SELECT course_site_id FROM course_site WHERE course_id = ?)""",
                COURSE_ID);
        jdbc.update("DELETE FROM site WHERE name LIKE ?", PREFIX + "%");
    }
}
