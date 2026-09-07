package com.templestamp.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 참배 요소(v4) API — 「가는 법」 7자리를 채우고 지우는 경로.
 * <p>
 * 여기서 지키는 것은 "관리자가 넣었다고 믿는데 화면에는 안 나오는" 상태를 만들지 않는 것이다.
 * 오타난 코드를 조용히 무시하면 정확히 그 상태가 되므로 400 으로 돌려주고,
 * 저장한 값은 같은 트랜잭션 밖에서 곧바로 {@code GET /api/sites/{id}/guide} 에 나와야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminSiteElementIntegrationTest {

    private static final String PREFIX = "ELEM-IT-";
    private static final String ADMIN_EMAIL = "admin@templestamp.local";
    private static final String ADMIN_PASSWORD = "Admin1234!";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;

    private String adminToken;
    private long siteId;

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
        adminToken = loginAsAdmin();
        siteId = createSite();
        // 「가는 법」은 ACTIVE 사찰만 연다. 요소를 넣어도 화면에 나오는지 보려면 공개 상태여야 한다.
        jdbc.update("UPDATE site SET status = 'ACTIVE' WHERE site_id = ?", siteId);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("없는 요소 코드는 400 COMMON-4000 + fields[elementCode] — 조용히 무시하지 않는다")
    void unknown_element_code_is_rejected() throws Exception {
        mvc.perform(adminPost("/api/admin/sites/" + siteId + "/elements", """
                        {"items":[{"elementCode":"NOT_A_GATE","localName":"없는문"}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-4000"))
                .andExpect(jsonPath("$.error.fields[0].field").value("elementCode"));

        assertThat(elementRows()).isZero();   // 한 건이라도 틀리면 전부 넣지 않는다
    }

    @Test
    @DisplayName("같은 코드를 두 번 보내도 site_element 는 1행 — 삭제 후 재삽입이 아니라 UPSERT 다")
    void resending_same_code_keeps_one_row() throws Exception {
        upsert("""
                {"items":[{"elementCode":"MAIN_HALL","localName":"대웅전"}]}""");
        upsert("""
                {"items":[{"elementCode":"MAIN_HALL","localName":"대적광전","note":"고쳐 적었다"}]}""");

        assertThat(elementRows()).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT local_name FROM site_element se JOIN shrine_element e ON e.element_id = se.element_id
                 WHERE se.site_id = ? AND e.code = 'MAIN_HALL'""", String.class, siteId))
                .isEqualTo("대적광전");
    }

    @Test
    @DisplayName("저장한 요소가 가는 법에 바로 나오고, 지우면 그 자리가 다시 '없음' 이 된다")
    void saved_element_shows_up_in_guide() throws Exception {
        upsert("""
                {"items":[{"elementCode":"ILJUMUN","localName":"조계문"},
                          {"elementCode":"MAIN_HALL","localName":"대웅전"}]}""");

        JsonNode iljumun = step("ILJUMUN");
        assertThat(iljumun.path("present").asBoolean()).isTrue();
        assertThat(iljumun.path("localName").asText()).isEqualTo("조계문");
        assertThat(step("PAGODA").path("present").asBoolean()).isFalse();   // 안 보낸 자리는 그대로 없음

        mvc.perform(delete("/api/admin/sites/" + siteId + "/elements/ILJUMUN")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());   // 삭제는 204 · 본문 없음(감사 G)

        JsonNode after = step("ILJUMUN");
        assertThat(after.path("present").asBoolean()).isFalse();
        assertThat(after.path("localName").isNull()).isTrue();
        assertThat(after.path("meaning").asText()).isNotBlank();           // 사전 문장은 없는 자리에서도 나간다
        assertThat(step("MAIN_HALL").path("present").asBoolean()).isTrue();  // 옆자리는 건드리지 않았다
    }

    /* ---------------- 내부 ---------------- */

    private void upsert(String body) throws Exception {
        mvc.perform(adminPost("/api/admin/sites/" + siteId + "/elements", body))
                .andExpect(status().isOk());
    }

    private JsonNode step(String code) throws Exception {
        String body = mvc.perform(get("/api/sites/" + siteId + "/guide"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode s : om.readTree(body).path("data").path("steps")) {
            if (code.equals(s.path("code").asText())) {
                return s;
            }
        }
        throw new AssertionError("가는 법 응답에 " + code + " 자리가 없다 — 7칸은 언제나 다 나와야 한다");
    }

    private Integer elementRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM site_element WHERE site_id = ?", Integer.class, siteId);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder adminPost(String url, String body) {
        return post(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private long createSite() throws Exception {
        String body = mvc.perform(adminPost("/api/admin/sites", """
                        {
                          "latitude": 37.5, "longitude": 127.0, "verifyRadius": 200,
                          "qrLocationHint": "일주문 안쪽 안내판",
                          "i18n": [{"locale":"ko","name":"%s사찰","description":"요소 테스트"}]
                        }""".formatted(PREFIX)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body).path("data").path("siteId").asLong();
    }

    private String loginAsAdmin() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(ADMIN_EMAIL, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body).path("data").path("accessToken").asText();
    }

    /** site_element 는 site 삭제에 CASCADE 로 딸려 간다. site_i18n 도 마찬가지다. */
    private void cleanUp() {
        jdbc.update("DELETE FROM site WHERE name LIKE ?", PREFIX + "%");
    }
}
