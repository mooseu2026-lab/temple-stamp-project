package com.templestamp.content.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 조사 CSV(정본) ↔ 적재된 DB 대조. ch4-close.md §3-2 의 1·2·5 를 여기서 본다.
 * <p>
 * <b>왜 필요한가</b> — 시드가 한 칸을 빠뜨려도 화면은 멀쩡해 보인다. 「가는 법」은 언제나 7칸이 나오고,
 * 없는 자리는 "없음" 으로 정상 표시되기 때문이다. "잘 나온다" 가 검수 근거가 못 되므로
 * 원본 CSV 와 한 줄씩 맞춰 보는 수밖에 없다.
 * <p>
 * 시드를 아직 안 돌린 DB 에서는 통째로 건너뛴다({@link Assumptions}) — 시드는 개발자가
 * {@code --spring.profiles.active=local,seed} 로 한 번 직접 돌리는 것이지 테스트가 돌리는 것이 아니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SeedConsistencyTest {

    /** CSV 열 이름 → shrine_element.sort_no. 주불전(6)·부속전각(7)은 Y/N 열이 없고 이름 칸의 유무가 곧 보유다. */
    private static final Map<String, Integer> FLAG_COLUMNS = new LinkedHashMap<>();
    private static final String SKIP_MARK = "시드 미투입";

    static {
        FLAG_COLUMNS.put("iljumun", 1);
        FLAG_COLUMNS.put("geumgangmun", 2);
        FLAG_COLUMNS.put("cheonwangmun", 3);
        FLAG_COLUMNS.put("bulimun", 4);
        FLAG_COLUMNS.put("pagoda", 5);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;

    private List<Map<String, String>> csv;

    @BeforeEach
    void loadCsv() throws Exception {
        csv = SeedCsv.read("seed/sites-master.csv");
        Assumptions.assumeTrue(seededSites() > 0,
                "시드를 돌리지 않은 DB 다 — --spring.profiles.active=local,seed 로 한 번 적재한 뒤에 본다");
    }

    @Test
    @DisplayName("CSV 의 Y 개수와 site_element 행 수가 자리별로 같다")
    void element_rows_match_csv() {
        Map<Integer, Integer> expected = new LinkedHashMap<>();
        for (int sortNo = 1; sortNo <= 7; sortNo++) {
            expected.put(sortNo, 0);
        }
        for (var row : seeded()) {
            FLAG_COLUMNS.forEach((col, sortNo) -> {
                if (SeedCsv.yes(row.get(col))) {
                    expected.merge(sortNo, 1, Integer::sum);
                }
            });
            if (row.get("main_hall_name") != null) expected.merge(6, 1, Integer::sum);
            if (row.get("annex_halls") != null) expected.merge(7, 1, Integer::sum);
        }

        List<String> wrong = new ArrayList<>();
        for (var e : expected.entrySet()) {
            Integer actual = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM site_element se
                      JOIN shrine_element sh ON sh.element_id = se.element_id
                      JOIN site s ON s.site_id = se.site_id
                     WHERE sh.sort_no = ? AND s.seed_key IS NOT NULL""", Integer.class, e.getKey());
            if (!e.getValue().equals(actual)) {
                wrong.add("sort_no=" + e.getKey() + " CSV=" + e.getValue() + " DB=" + actual);
            }
        }
        assertThat(wrong).as("자리별 보유 행 수가 CSV 와 다르다").isEmpty();
    }

    @Test
    @DisplayName("main_hall_name 이 주불전(sort 6)의 local_name 으로 그대로 들어갔다")
    void main_hall_names_match_csv() {
        List<String> wrong = new ArrayList<>();
        for (var row : seeded()) {
            if (row.get("main_hall_name") == null) {
                continue;
            }
            String key = seedKey(row);
            List<String> stored = jdbc.queryForList("""
                    SELECT se.local_name FROM site_element se
                      JOIN shrine_element sh ON sh.element_id = se.element_id AND sh.sort_no = 6
                      JOIN site s ON s.site_id = se.site_id
                     WHERE s.seed_key = ?""", String.class, key);
            if (stored.size() != 1 || !row.get("main_hall_name").equals(stored.get(0))) {
                wrong.add(key + " main_hall_name CSV=" + row.get("main_hall_name") + " DB=" + stored);
            }
        }
        assertThat(wrong).isEmpty();
    }

    @Test
    @DisplayName("flower_badge 가 있는 사찰마다 site_badge FLOWER 가 한 행씩 있다")
    void flower_badges_match_csv() {
        List<String> wrong = new ArrayList<>();
        for (var row : seeded()) {
            String key = seedKey(row);
            int expected = row.get("flower_badge") == null ? 0 : 1;
            Integer actual = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM site_badge b JOIN site s ON s.site_id = b.site_id
                     WHERE s.seed_key = ? AND b.badge_type = 'FLOWER'""", Integer.class, key);
            if (expected != actual) {
                wrong.add(key + " FLOWER 기대=" + expected + " 실제=" + actual);
            }
        }
        assertThat(wrong).isEmpty();
    }

    @Test
    @DisplayName("가는 법 응답의 present 가 CSV 의 Y/N 과 같다 — 표본 사찰 전수 대조")
    void guide_present_flags_match_csv() throws Exception {
        List<String> wrong = new ArrayList<>();
        for (String name : List.of("조계사", "화엄사", "통도사", "부석사")) {
            for (var row : seeded()) {
                if (!name.equals(row.get("site_name_ko"))) {
                    continue;
                }
                Long siteId = siteIdOf(seedKey(row));
                if (siteId == null) {
                    continue;
                }
                JsonNode steps = guideStepsOfDraft(siteId);
                assertThat(steps).as("가는 법은 언제나 7칸이다").hasSize(7);
                for (int i = 0; i < 7; i++) {
                    boolean present = steps.get(i).path("present").asBoolean();
                    boolean expected = expectedPresent(row, i + 1);
                    if (present != expected) {
                        wrong.add(seedKey(row) + " sort=" + (i + 1) + " CSV=" + expected + " 응답=" + present);
                    }
                }
            }
        }
        assertThat(wrong).isEmpty();
    }

    @Test
    @DisplayName("라인 12개가 각각 5구를 갖고, 모든 구에 후보가 하나 이상 있다 (12 × 5 = 60)")
    void every_line_has_five_slots_with_candidates() {
        List<Map<String, Object>> lines = jdbc.queryForList("""
                SELECT c.course_id, c.name,
                       COUNT(DISTINCT cs.position) AS slots,
                       COUNT(DISTINCT CASE WHEN ss.slot_site_id IS NOT NULL THEN cs.position END) AS filled
                  FROM course c
                  JOIN course_site cs ON cs.course_id = c.course_id
                  LEFT JOIN slot_site ss ON ss.course_site_id = cs.course_site_id
                 WHERE c.name LIKE '%공양의 길'
                 GROUP BY c.course_id, c.name ORDER BY c.course_id""");

        assertThat(lines).as("라인(line_code)당 코스 하나 — 12개다").hasSize(12);
        List<String> wrong = new ArrayList<>();
        for (var line : lines) {
            long slots = ((Number) line.get("slots")).longValue();
            long filled = ((Number) line.get("filled")).longValue();
            if (slots != 5 || filled != 5) {
                wrong.add(line.get("name") + " slots=" + slots + " 후보있는자리=" + filled);
            }
        }
        assertThat(wrong).isEmpty();
    }

    @Test
    @DisplayName("후보의 권역이 코스의 권역과 같다 — CSV region_code 를 DB code 로 옮긴 기준으로")
    void candidates_stay_in_their_region() {
        List<Map<String, Object>> crossed = jdbc.queryForList("""
                SELECT c.name AS course_name, s.name AS site_name, s.seed_key, r.code AS course_region
                  FROM slot_site ss
                  JOIN course_site cs ON cs.course_site_id = ss.course_site_id
                  JOIN course c ON c.course_id = cs.course_id
                  JOIN region r ON r.region_id = c.region_id
                  JOIN site s ON s.site_id = ss.site_id
                 WHERE c.name LIKE '%공양의 길'
                   AND r.code <> CASE SUBSTRING_INDEX(s.seed_key, ':', 1)
                                   WHEN 'BUSAN_GYEONGNAM' THEN 'GYEONGNAM'
                                   WHEN 'DAEGU_GYEONGBUK' THEN 'GYEONGBUK'
                                   WHEN 'JEONNAM_GWANGJU' THEN 'JEONNAM'
                                   ELSE SUBSTRING_INDEX(s.seed_key, ':', 1) END""");
        assertThat(crossed).isEmpty();
    }

    @Test
    @DisplayName("자리의 대표(course_site.site_id)가 그 자리 MAIN 1번 후보와 같다")
    void representative_is_the_first_main_candidate() {
        List<Map<String, Object>> mismatched = jdbc.queryForList("""
                SELECT c.name, cs.position, cs.site_id AS representative, ss.site_id AS main_first
                  FROM course_site cs
                  JOIN course c ON c.course_id = cs.course_id
                  LEFT JOIN slot_site ss
                    ON ss.course_site_id = cs.course_site_id AND ss.track = 'MAIN' AND ss.sort_no = 1
                 WHERE c.name LIKE '%공양의 길'
                   AND (ss.site_id IS NULL OR ss.site_id <> cs.site_id)""");
        assertThat(mismatched).isEmpty();
    }

    @Test
    @DisplayName("과포화 사찰이 유일한 후보인 자리도 공개 목록이 비지 않는다 (부산·동부 1·2구)")
    void congested_only_slots_still_expose_a_candidate() {
        List<Map<String, Object>> empty = jdbc.queryForList("""
                SELECT c.name AS course_name, cs.position, s.name AS representative
                  FROM course_site cs
                  JOIN course c ON c.course_id = cs.course_id
                  JOIN site s ON s.site_id = cs.site_id
                 WHERE c.name LIKE '%공양의 길'
                   AND NOT EXISTS (SELECT 1 FROM slot_site ss
                                    WHERE ss.course_site_id = cs.course_site_id)
                 ORDER BY c.course_id, cs.position""");
        assertThat(empty).as("후보 행이 아예 없는 자리는 없어야 한다").isEmpty();

        // 과포화가 유일한 후보인 자리 — 목록에서 빼면 여기가 0이 된다. 빼지 않으므로 1개 이상이어야 한다.
        List<Map<String, Object>> congestedOnly = jdbc.queryForList("""
                SELECT c.name AS course_name, cs.position, s.name AS representative,
                       COUNT(*) AS candidates, SUM(ss.is_congested) AS congested
                  FROM course_site cs
                  JOIN course c ON c.course_id = cs.course_id
                  JOIN site s ON s.site_id = cs.site_id
                  JOIN slot_site ss ON ss.course_site_id = cs.course_site_id
                 WHERE c.name LIKE '%공양의 길'
                 GROUP BY c.course_id, c.name, cs.position, s.name
                HAVING COUNT(*) = SUM(ss.is_congested)
                 ORDER BY c.course_id, cs.position""");
        for (var slot : congestedOnly) {
            long candidates = ((Number) slot.get("candidates")).longValue();
            assertThat(candidates)
                    .as("%s %s구(%s)", slot.get("course_name"), slot.get("position"), slot.get("representative"))
                    .isGreaterThanOrEqualTo(1);
        }
        assertThat(congestedOnly)
                .as("교재 Q3 대로면 부산·동부 1·2구가 여기 잡힌다 — 후보는 남아 있고 congested 로만 표시된다")
                .hasSizeGreaterThanOrEqualTo(1);
    }

    /* ---------------- 내부 ---------------- */

    /** note 에 "시드 미투입" 이 있는 행(묘각사)은 애초에 넣지 않았으므로 대조 대상이 아니다. */
    private List<Map<String, String>> seeded() {
        return csv.stream()
                .filter(r -> r.get("note") == null || !r.get("note").contains(SKIP_MARK))
                .toList();
    }

    private boolean expectedPresent(Map<String, String> row, int sortNo) {
        if (sortNo == 6) return row.get("main_hall_name") != null;
        if (sortNo == 7) return row.get("annex_halls") != null;
        return FLAG_COLUMNS.entrySet().stream()
                .filter(e -> e.getValue() == sortNo)
                .anyMatch(e -> SeedCsv.yes(row.get(e.getKey())));
    }

    /**
     * 시드가 넣은 사찰은 전부 DRAFT 다 — {@code qr_location_hint} 를 현장에서 정하기 때문이고,
     * 「가는 법」은 ACTIVE 만 연다. 응답 자체를 봐야 하므로 잠깐 공개로 올렸다가 되돌린다.
     * DB 를 직접 건드리는 이유는 관리자 PATCH 가 QR 힌트를 요구해서다(그 규칙은 그대로 두는 것이 맞다).
     */
    private JsonNode guideStepsOfDraft(long siteId) throws Exception {
        String before = jdbc.queryForObject("SELECT status FROM site WHERE site_id = ?", String.class, siteId);
        jdbc.update("UPDATE site SET status = 'ACTIVE' WHERE site_id = ?", siteId);
        try {
            String body = mvc.perform(get("/api/sites/" + siteId + "/guide"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            return om.readTree(body).path("data").path("steps");
        } finally {
            jdbc.update("UPDATE site SET status = ? WHERE site_id = ?", before, siteId);
        }
    }

    private Long siteIdOf(String seedKey) {
        List<Long> ids = jdbc.queryForList("SELECT site_id FROM site WHERE seed_key = ?", Long.class, seedKey);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Integer seededSites() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM site WHERE seed_key IS NOT NULL", Integer.class);
    }

    private static String seedKey(Map<String, String> row) {
        return row.get("region_code") + ":" + row.get("site_name_ko") + ":"
                + (row.get("disambiguation") == null ? "" : row.get("disambiguation"));
    }
}
