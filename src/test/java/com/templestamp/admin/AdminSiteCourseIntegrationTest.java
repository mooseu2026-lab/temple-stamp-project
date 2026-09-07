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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 사찰·코스 등록 규칙을 실제 MySQL 로 태운다.
 * <p>
 * 여기서 지키는 것은 전부 {@code @Valid} 가 못 잡는 조건들이다 — 목록 안에 ko 가 있는가,
 * 다른 코스가 이미 가진 사찰인가, 걷는 중인 순례가 있는가. 이런 것은 원소 하나만 봐서는 알 수 없고
 * DB 를 봐야 알 수 있어서, 컨트롤러부터 DB 까지 그대로 지나야만 검증된다.
 * <p>
 * 테스트가 만든 사찰·코스는 이름 접두어로 골라 매번 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminSiteCourseIntegrationTest {

    private static final String PREFIX = "ADMIN-IT-";
    private static final String ADMIN_EMAIL = "admin@templestamp.local";
    private static final String ADMIN_PASSWORD = "Admin1234!";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
        adminToken = loginAsAdmin();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    /* ---------------- 사찰 ---------------- */

    @Test
    @DisplayName("i18n 에 ko 가 없으면 400 COMMON-4000 이고 fields 가 i18n 을 가리킨다")
    void site_without_ko_is_rejected() throws Exception {
        mvc.perform(adminPost("/api/admin/sites", siteBody("en")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-4000"))
                .andExpect(jsonPath("$.error.fields[0].field").value("i18n"));
    }

    @Test
    @DisplayName("ko 를 넣으면 201 로 만들어진다")
    void site_with_ko_is_created() throws Exception {
        long siteId = createSite("ko");
        assertThat(siteId).isPositive();

        String status = jdbc.queryForObject(
                "SELECT status FROM site WHERE site_id = ?", String.class, siteId);
        assertThat(status).isEqualTo("DRAFT");   // 등록만으로는 공개되지 않는다
    }

    @Test
    @DisplayName("본문에 status 를 실으면 400 — 조용히 무시하지 않고 거절한다")
    void status_in_body_is_rejected() throws Exception {
        String body = """
                {
                  "latitude": 37.5, "longitude": 127.0, "verifyRadius": 200,
                  "qrLocationHint": "일주문 안쪽 안내판", "status": "ACTIVE",
                  "i18n": [{"locale":"ko","name":"%s사찰","description":"통합테스트"}]
                }""".formatted(PREFIX);

        // FAIL_ON_UNKNOWN_PROPERTIES 가 켜져 있어(JacksonConfig) 모르는 필드는 400 이다.
        // 조용히 무시하면 관리자는 "공개했다" 고 믿는데 실제로는 DRAFT 인 상태가 된다 — 그게 더 나쁘다.
        mvc.perform(adminPost("/api/admin/sites", body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-4004"));
    }

    @Test
    @DisplayName("등록은 언제나 DRAFT 이고, 내용 수정이 공개 여부를 바꾸지 않는다")
    void save_never_changes_status() throws Exception {
        long siteId = createSite("ko");
        assertThat(statusOf(siteId)).isEqualTo("DRAFT");   // 등록만으로는 공개되지 않는다

        // 공개는 PATCH 로만 — 여기서 ACTIVE 조건(qr 힌트·ko 행)을 검사한다
        mvc.perform(adminPatch("/api/admin/sites/" + siteId + "/status", """
                {"status":"ACTIVE"}""")).andExpect(status().isOk());
        assertThat(statusOf(siteId)).isEqualTo("ACTIVE");

        // 내용만 고치는 PUT 은 상태를 건드리지 않는다 (SET 절에 status 가 없다)
        mvc.perform(adminPut("/api/admin/sites/" + siteId, siteBody("ko", PREFIX + "사찰수정")))
                .andExpect(status().isOk());
        assertThat(statusOf(siteId)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("qrLocationHint 가 없으면 ACTIVE 로 올릴 수 없다 — 409 ADMIN-4092")
    void activating_without_qr_hint_is_blocked() throws Exception {
        long siteId = createSite("ko");
        jdbc.update("UPDATE site SET qr_location_hint = NULL WHERE site_id = ?", siteId);

        mvc.perform(adminPatch("/api/admin/sites/" + siteId + "/status", """
                        {"status":"ACTIVE"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ADMIN-4092"));

        // 조건을 채우면 통과한다 — 막는 것이 조건이지 상태 전이 자체가 아니라는 확인
        jdbc.update("UPDATE site SET qr_location_hint = '일주문 안쪽' WHERE site_id = ?", siteId);
        mvc.perform(adminPatch("/api/admin/sites/" + siteId + "/status", """
                        {"status":"ACTIVE"}"""))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("같은 i18n 을 다시 보내도 행 수가 늘지 않는다 (UPSERT)")
    void resending_i18n_does_not_add_rows() throws Exception {
        long siteId = createSite("ko");
        int before = i18nCount(siteId);

        mvc.perform(adminPut("/api/admin/sites/" + siteId, siteBody("ko")))
                .andExpect(status().isOk());

        assertThat(i18nCount(siteId)).isEqualTo(before);
    }

    @Test
    @DisplayName("관리자 목록은 DRAFT 사찰도 보여준다")
    void admin_list_includes_draft() throws Exception {
        createSite("ko");

        mvc.perform(get("/api/admin/sites").param("q", PREFIX)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    @DisplayName("ACTIVE 코스에 배정된 사찰은 DRAFT 로도 내릴 수 없다 — 409 COURSE-4092")
    void draft_rollback_is_blocked_while_assigned_to_active_course() throws Exception {
        long[] siteIds = createFiveSites();
        long courseId = createCourse(siteIds);

        // 사찰 5곳 ACTIVE → 코스 ACTIVE
        for (long id : siteIds) {
            mvc.perform(adminPatch("/api/admin/sites/" + id + "/status", """
                    {"status":"ACTIVE"}""")).andExpect(status().isOk());
        }
        mvc.perform(adminPatch("/api/admin/courses/" + courseId + "/status", """
                {"status":"ACTIVE"}""")).andExpect(status().isOk());

        // 코스의 ACTIVE 조건이 "5곳 전부 ACTIVE" 라, 뒤에서 한 곳이 빠지면 그 조건이 조용히 깨진다.
        // INACTIVE 든 DRAFT 든 결과가 같으므로 같은 검사를 받는다.
        for (String down : new String[]{"DRAFT", "INACTIVE"}) {
            mvc.perform(adminPatch("/api/admin/sites/" + siteIds[0] + "/status",
                            "{\"status\":\"%s\"}".formatted(down)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("COURSE-4092"));
        }

        // 코스를 먼저 내리면 통과한다
        mvc.perform(adminPatch("/api/admin/courses/" + courseId + "/status", """
                {"status":"INACTIVE"}""")).andExpect(status().isOk());
        mvc.perform(adminPatch("/api/admin/sites/" + siteIds[0] + "/status", """
                {"status":"DRAFT"}""")).andExpect(status().isOk());
    }

    /* ---------------- 공개 조회는 ACTIVE 만 ---------------- */

    @Test
    @DisplayName("INACTIVE 코스 상세는 공개 경로에서 404 COURSE-4041")
    void inactive_course_detail_is_404_on_public_path() throws Exception {
        long[] siteIds = createFiveSites();
        long courseId = createCourse(siteIds);
        jdbc.update("UPDATE course SET status = 'INACTIVE' WHERE course_id = ?", courseId);

        mvc.perform(get("/api/courses/" + courseId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COURSE-4041"));

        // 관리자 경로는 status 를 가리지 않는다 — 내린 코스를 다시 손보려면 보여야 하기 때문이다
        mvc.perform(adminPut("/api/admin/courses/" + courseId, courseBody(siteIds, false)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DRAFT 사찰의 싱글페이지는 404 SITE-4040 — 목록에서 내린 것이 링크로 살아 있으면 안 된다")
    void draft_site_page_is_404() throws Exception {
        long siteId = createSite("ko");   // 등록 직후는 DRAFT

        mvc.perform(get("/api/sites/" + siteId + "/page"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SITE-4040"));
        mvc.perform(get("/api/sites/" + siteId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SITE-4040"));
        mvc.perform(get("/api/sites/" + siteId + "/guide"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SITE-4040"));

        // 관리자 목록에는 그대로 보인다
        mvc.perform(get("/api/admin/sites").param("q", PREFIX)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    /* ---------------- 코스 ---------------- */

    @Test
    @DisplayName("사찰이 4곳이면 400 — 코스는 정확히 5곳이다")
    void course_with_four_sites_is_rejected() throws Exception {
        mvc.perform(adminPost("/api/admin/courses", courseBody(new long[]{1, 2, 3, 4}, false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-4000"));
    }

    @Test
    @DisplayName("이미 다른 코스가 가진 사찰이면 409 COURSE-4093")
    void site_already_in_another_course_is_rejected() throws Exception {
        // 시드의 코스 1 이 사찰 1~5 를 갖고 있다. 그중 하나를 새 코스에 넣으려 한다.
        mvc.perform(adminPost("/api/admin/courses", courseBody(new long[]{1, 2, 3, 4, 5}, false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COURSE-4093"));
    }

    @Test
    @DisplayName("position 이 겹치면 400 COMMON-4000 + fields[sites] — 404 COURSE-4042 가 아니다")
    void duplicate_position_is_a_validation_error() throws Exception {
        long[] siteIds = createFiveSites();
        // 1번 자리를 두 번 쓴다. verseNo 는 position 과 맞춰 두어야 COURSE-4001 이 먼저 걸리지 않는다.
        String body = """
                {"regionId":1,"name":"%s중복코스","sites":[
                  {"siteId":%d,"position":1,"verseNo":1},
                  {"siteId":%d,"position":1,"verseNo":1},
                  {"siteId":%d,"position":3,"verseNo":3},
                  {"siteId":%d,"position":4,"verseNo":4},
                  {"siteId":%d,"position":5,"verseNo":5}]}"""
                .formatted(PREFIX, siteIds[0], siteIds[1], siteIds[2], siteIds[3], siteIds[4]);

        mvc.perform(adminPost("/api/admin/courses", body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-4000"))
                .andExpect(jsonPath("$.error.fields[0].field").value("sites"));
    }

    @Test
    @DisplayName("verseNo 가 position 과 다르면 400 COURSE-4001")
    void verse_no_must_equal_position() throws Exception {
        long[] siteIds = createFiveSites();

        mvc.perform(adminPost("/api/admin/courses", courseBody(siteIds, true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COURSE-4001"));
    }

    @Test
    @DisplayName("진행 중인 순례가 있으면 코스 구성을 바꿀 수 없다 — 409 COURSE-4092")
    void course_with_walking_pilgrimage_cannot_be_changed() throws Exception {
        long[] siteIds = createFiveSites();
        long courseId = createCourse(siteIds);

        Long userId = jdbc.queryForObject(
                "SELECT user_id FROM users WHERE email = ?", Long.class, ADMIN_EMAIL);
        jdbc.update("INSERT INTO pilgrimage (user_id, course_id, status) VALUES (?, ?, 'IN_PROGRESS')",
                userId, courseId);
        try {
            mvc.perform(adminPut("/api/admin/courses/" + courseId, courseBody(siteIds, false)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("COURSE-4092"));
        } finally {
            jdbc.update("DELETE FROM pilgrimage WHERE course_id = ?", courseId);
        }
    }

    /* ---------------- 이동시간 ---------------- */

    @Test
    @DisplayName("site-distances 는 같은 요청을 두 번 보내도 행 수가 같다 (UPSERT 멱등)")
    void site_distances_are_idempotent() throws Exception {
        long[] s = createFiveSites();
        String body = """
                {"items":[
                  {"siteAId":%d,"siteBId":%d,"minMinutes":40},
                  {"siteAId":%d,"siteBId":%d,"minMinutes":35}
                ]}""".formatted(s[0], s[1], s[1], s[0]);

        mvc.perform(adminPut("/api/admin/site-distances", body)).andExpect(status().isOk());
        int after1 = distanceCount();
        mvc.perform(adminPut("/api/admin/site-distances", body)).andExpect(status().isOk());

        assertThat(distanceCount()).isEqualTo(after1);
    }

    @Test
    @DisplayName("출발과 도착이 같으면 400")
    void same_site_distance_is_rejected() throws Exception {
        long[] s = createFiveSites();
        mvc.perform(adminPut("/api/admin/site-distances", """
                        {"items":[{"siteAId":%d,"siteBId":%d,"minMinutes":10}]}""".formatted(s[0], s[0])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-4000"));
    }

    /* ---------------- 권한 ---------------- */

    @Test
    @DisplayName("토큰 없이 관리자 경로에 오면 401")
    void admin_path_without_token_is_401() throws Exception {
        mvc.perform(get("/api/admin/sites"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH-4013"));
    }

    /* ---------------- 도우미 ---------------- */

    private MockHttpServletRequestBuilder adminPost(String url, String body) {
        return post(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder adminPut(String url, String body) {
        return put(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder adminPatch(String url, String body) {
        return patch(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String siteBody(String locale) {
        return siteBody(locale, PREFIX + "사찰");
    }

    /** 좌표를 본문에 담는다 — CoordinateFieldGuard 화이트리스트가 이 경로를 열어 두는지도 함께 확인된다. */
    private String siteBody(String locale, String name) {
        return """
                {
                  "latitude": 37.5, "longitude": 127.0, "verifyRadius": 200,
                  "qrLocationHint": "일주문 안쪽 안내판",
                  "i18n": [{"locale":"%s","name":"%s","description":"통합테스트"}]
                }""".formatted(locale, name);
    }

    private String courseBody(long[] siteIds, boolean mismatchVerse) {
        StringBuilder slots = new StringBuilder();
        for (int i = 0; i < siteIds.length; i++) {
            int position = i + 1;
            int verseNo = mismatchVerse ? (position % 5) + 1 : position;
            if (i > 0) slots.append(",");
            slots.append("{\"siteId\":%d,\"position\":%d,\"verseNo\":%d}"
                    .formatted(siteIds[i], position, verseNo));
        }
        return """
                {"regionId":1,"name":"%s코스","sites":[%s]}""".formatted(PREFIX, slots);
    }

    private long createSite(String locale) throws Exception {
        return createSite(locale, PREFIX + "사찰");
    }

    private long createSite(String locale, String name) throws Exception {
        String body = mvc.perform(adminPost("/api/admin/sites", siteBody(locale, name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = om.readTree(body).path("data");
        return data.path("siteId").asLong();
    }

    private long[] createFiveSites() throws Exception {
        long[] ids = new long[5];
        for (int i = 0; i < 5; i++) {
            ids[i] = createSite("ko", PREFIX + "사찰" + i);
        }
        return ids;
    }

    private long createCourse(long[] siteIds) throws Exception {
        String body = mvc.perform(adminPost("/api/admin/courses", courseBody(siteIds, false)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body).path("data").path("courseId").asLong();
    }

    private String statusOf(long siteId) {
        return jdbc.queryForObject("SELECT status FROM site WHERE site_id = ?", String.class, siteId);
    }

    private int i18nCount(long siteId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM site_i18n WHERE site_id = ?", Integer.class, siteId);
    }

    private int distanceCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM site_distance", Integer.class);
    }

    private String loginAsAdmin() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(ADMIN_EMAIL, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body).path("data").path("accessToken").asText();
    }

    /** course_site → course → site_distance → site 순. FK 가 RESTRICT 라 순서가 중요하다. */
    private void cleanUp() {
        jdbc.update("""
                DELETE FROM course_site WHERE site_id IN (SELECT site_id FROM site WHERE name LIKE ?)
                """, PREFIX + "%");
        jdbc.update("DELETE FROM course_site WHERE course_id IN (SELECT course_id FROM course WHERE name LIKE ?)",
                PREFIX + "%");
        jdbc.update("DELETE FROM pilgrimage WHERE course_id IN (SELECT course_id FROM course WHERE name LIKE ?)",
                PREFIX + "%");
        jdbc.update("DELETE FROM course WHERE name LIKE ?", PREFIX + "%");
        jdbc.update("""
                DELETE FROM site_distance
                WHERE site_a_id IN (SELECT site_id FROM site WHERE name LIKE ?)
                   OR site_b_id IN (SELECT site_id FROM site WHERE name LIKE ?)
                """, PREFIX + "%", PREFIX + "%");
        jdbc.update("DELETE FROM site WHERE name LIKE ?", PREFIX + "%");
    }
}
