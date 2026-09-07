package com.templestamp.site;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 「사찰 가는 법」 종단 확인. 실제 MySQL 의 shrine_element·site_element 를 그대로 지난다.
 * <p>
 * 여기서 지키려는 계약은 하나다 — <b>어느 절이든 언제나 7자리</b>. 이 계약이 깨지는 경로가
 * 코드가 아니라 SQL(LEFT JOIN 조건을 WHERE 로 옮기는 실수)에 있어서, 매퍼를 실제로 태워야만 잡힌다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SiteGuideIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("사전은 7행이고 순서 번호가 1~7 로 한 번씩만 쓰인다")
    void dictionary_has_exactly_seven_ordered_rows() {
        List<Integer> sortNos = jdbc.queryForList(
                "SELECT sort_no FROM shrine_element ORDER BY sort_no", Integer.class);
        assertThat(sortNos).containsExactly(1, 2, 3, 4, 5, 6, 7);
    }

    @Test
    @DisplayName("보유 요소가 가장 적은 절도 7자리가 모두 온다 — 없는 자리는 빠지지 않는다")
    void sparse_site_still_returns_seven_steps() throws Exception {
        // 조계사(1)는 산문 세 곳과 탑 자리가 비어 있다
        JsonNode steps = guideSteps(1L);

        assertThat(steps).hasSize(7);
        for (int i = 0; i < 7; i++) {
            assertThat(steps.get(i).path("position").asInt()).isEqualTo(i + 1);
        }
        assertThat(codes(steps)).containsExactly(
                "ILJUMUN", "GEUMGANGMUN", "CHEONWANGMUN", "BURIMUN", "PAGODA", "MAIN_HALL", "ANNEX_HALL");

        // 있는 자리와 없는 자리가 둘 다 있어야 아래 규칙들을 검증할 수 있다
        assertThat(steps).anyMatch(s -> s.path("present").asBoolean());
        assertThat(steps).anyMatch(s -> !s.path("present").asBoolean());
    }

    /**
     * 시드의 다섯 절은 모두 요소를 하나 이상 갖고 있어, 아래 두 경로가 시드만으로는 실행되지 않는다.
     * 임시 사찰을 만들어 실제로 태운다. site_element FK 가 ON DELETE CASCADE 라 절만 지우면 정리된다.
     */
    @Test
    @DisplayName("요소를 하나도 등록하지 않은 절도 7자리가 오고 길 안내가 모두 채워진다")
    void site_with_no_elements_returns_seven_steps_and_single_last_passage() throws Exception {
        long siteId = createBareSite();
        try {
            JsonNode steps = guideSteps(siteId);

            assertThat(steps).hasSize(7);
            assertThat(steps).allMatch(s -> !s.path("present").asBoolean());

            // 요소가 하나도 없어도 길 안내는 7칸 모두 채워진다 — 걷는 순서는 절 구성과 무관하다
            for (int i = 0; i < 7; i++) {
                assertThat(steps.get(i).path("passage").asText())
                        .as("자리 %d 의 길 안내", i + 1).isNotBlank();
            }
        } finally {
            jdbc.update("DELETE FROM site WHERE site_id = ?", siteId);
        }
    }

    /** ACTIVE 로 만든다 — 공개 조회는 ACTIVE 만 연다(ch4 Q3). DRAFT 면 404 다. */
    private long createBareSite() {
        long siteId = 990001L;
        jdbc.update("DELETE FROM site WHERE site_id = ?", siteId);
        jdbc.update("""
                INSERT INTO site (site_id, name, latitude, longitude, verify_radius, status)
                VALUES (?, '테스트 임시 사찰', 37.0, 127.0, 100, 'ACTIVE')
                """, siteId);
        return siteId;
    }

    @Test
    @DisplayName("없는 자리는 의미를 그대로 주고 그 절의 이름·예법·비고는 비운다")
    void absent_step_keeps_meaning_but_drops_site_specific_fields() throws Exception {
        JsonNode absent = firstMatching(guideSteps(1L), false);

        assertThat(absent.path("meaning").asText()).isNotBlank();   // 사전 문장은 없는 절에서도 나간다
        assertThat(absent.path("localName").isNull()).isTrue();
        assertThat(absent.path("etiquette").isNull()).isTrue();
        assertThat(absent.path("note").isNull()).isTrue();
    }

    @Test
    @DisplayName("각 자리가 자기 오는 길 한 문장씩 — 7칸이 P1~P7 을 한 번씩 (D3 확정)")
    void each_step_carries_its_own_passage_once() throws Exception {
        // 조계사(1)는 1~5 가 비고 6·7 만 있다. 빈 구간이 다섯 칸 연속이다.
        JsonNode steps = guideSteps(1L);

        // P1~P7 이 정확히 한 번씩, 사전 순서 그대로 나온다
        List<String> expected = List.of(
                passageMeaning("ILJUMUN"), passageMeaning("GEUMGANGMUN"), passageMeaning("CHEONWANGMUN"),
                passageMeaning("BURIMUN"), passageMeaning("PAGODA"), passageMeaning("MAIN_HALL"),
                passageMeaning("ANNEX_HALL"));

        List<String> actual = new java.util.ArrayList<>();
        for (JsonNode s : steps) actual.add(s.path("passage").asText());

        assertThat(actual).containsExactlyElementsOf(expected);
        assertThat(actual).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("없는 자리도 자기 오는 길 한 문장뿐이다")
    void gap_step_does_not_borrow_next_passage() throws Exception {
        // 진관사(5)는 금강문(2)·탑(5)만 비어 있어 두 자리 모두 한 칸짜리 구간이다
        JsonNode steps = guideSteps(5L);

        JsonNode geumgang = steps.get(1);
        assertThat(geumgang.path("code").asText()).isEqualTo("GEUMGANGMUN");
        assertThat(geumgang.path("present").asBoolean()).isFalse();
        // 없는 자리도 자기 오는 길 한 문장뿐이다 — 다음 자리 문장을 끌어오지 않는다
        assertThat(geumgang.path("passage").asText()).isEqualTo(passageMeaning("GEUMGANGMUN"));
    }

    @Test
    @DisplayName("있는 자리의 passage 는 오는 길 한 문장뿐이고 그 절의 이름이 함께 온다")
    void present_step_uses_single_passage_and_local_name() throws Exception {
        JsonNode mainHall = firstByCode(guideSteps(3L), "MAIN_HALL");   // 화계사 = 대적광전

        assertThat(mainHall.path("present").asBoolean()).isTrue();
        assertThat(mainHall.path("name").asText()).isEqualTo("주불전");
        assertThat(mainHall.path("localName").asText()).isEqualTo("대적광전");
        assertThat(mainHall.path("etiquette").asText()).isNotBlank();
        assertThat(mainHall.path("passage").asText()).isEqualTo(passageMeaning("MAIN_HALL"));
    }

    @Test
    @DisplayName("없는 사찰은 404 SITE-4040")
    void unknown_site_returns_4040() throws Exception {
        mvc.perform(get("/api/sites/999999/guide"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SITE-4040"));
    }

    @Test
    @DisplayName("가는 법은 로그인 없이도 열린다")
    void guide_is_public() throws Exception {
        mvc.perform(get("/api/sites/1/guide"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.siteName").isNotEmpty());
    }

    /* ---------------- 도우미 ---------------- */

    private JsonNode guideSteps(Long siteId) throws Exception {
        String body = mvc.perform(get("/api/sites/{id}/guide", siteId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body).path("data").path("steps");
    }

    private List<String> codes(JsonNode steps) {
        return steps.findValuesAsText("code");
    }

    private JsonNode firstMatching(JsonNode steps, boolean present) {
        for (JsonNode s : steps) {
            if (s.path("present").asBoolean() == present) return s;
        }
        throw new AssertionError("present=" + present + " 인 자리가 없다");
    }

    private JsonNode firstByCode(JsonNode steps, String code) {
        for (JsonNode s : steps) {
            if (code.equals(s.path("code").asText())) return s;
        }
        throw new AssertionError(code + " 자리가 없다");
    }

    private String passageMeaning(String code) {
        return jdbc.queryForObject(
                "SELECT passage_meaning FROM shrine_element WHERE code = ?", String.class, code);
    }
}
