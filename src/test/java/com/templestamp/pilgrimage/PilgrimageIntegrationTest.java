package com.templestamp.pilgrimage;

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

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 순례 시작·여권 종단 확인 (교재 [기본 40]~[기본 43]).
 * <p>
 * 여기서 지키는 것 셋 —
 * ① 시작이 <b>멱등</b>하다. 같은 코스를 두 번 눌러도 순례는 하나뿐이고 둘 다 200 이다.
 * ② 여권이 <b>미시작 코스도 보여준다</b>. SQL 의 {@code p.user_id} 조건이 WHERE 로 내려가면 통째로 사라지는데,
 *    화면만 봐서는 "아직 시작한 게 없어서 비었나" 로 읽혀 오래 모른다.
 * ③ 요약 4개가 저장값이 아니라 <b>센 값</b>이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PilgrimageIntegrationTest {

    private static final String EMAIL = "pilgrim-it@test.com";
    private static final String PASSWORD = "Check!2026";
    private static final long ACTIVE_COURSE_ID = 1L;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;

    private String token;
    private Long userId;

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
        signup();
        token = login();
        userId = jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, EMAIL);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    /* ---------------- 시작 ---------------- */

    @Test
    @DisplayName("처음 시작하면 created:true 이고 IN_PROGRESS 다")
    void first_start_creates() throws Exception {
        JsonNode data = start(ACTIVE_COURSE_ID).path("data");

        assertThat(data.path("created").asBoolean()).isTrue();
        assertThat(data.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(data.path("pilgrimageId").asLong()).isPositive();
        assertThat(data.path("startedAt").isNull()).isFalse();   // DB DEFAULT 가 채운 값을 재조회해 담는다
    }

    @Test
    @DisplayName("같은 코스를 다시 눌러도 200 이고 created:false, 같은 pilgrimageId 다")
    void second_start_is_idempotent() throws Exception {
        long first = start(ACTIVE_COURSE_ID).path("data").path("pilgrimageId").asLong();
        JsonNode again = start(ACTIVE_COURSE_ID).path("data");

        assertThat(again.path("created").asBoolean()).isFalse();
        assertThat(again.path("pilgrimageId").asLong()).isEqualTo(first);
        assertThat(pilgrimageCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("ACTIVE 가 아닌 코스는 409 COURSE-4092 — 없는 코스도 같은 응답이다")
    void inactive_or_missing_course_is_409() throws Exception {
        // 없는 코스
        mvc.perform(startRequest(999999L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COURSE-4092"));

        // 있지만 내려간 코스 — 존재 여부를 응답으로 구분할 수 없어야 한다
        jdbc.update("UPDATE course SET status = 'INACTIVE' WHERE course_id = ?", ACTIVE_COURSE_ID);
        try {
            mvc.perform(startRequest(ACTIVE_COURSE_ID))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("COURSE-4092"));
        } finally {
            jdbc.update("UPDATE course SET status = 'ACTIVE' WHERE course_id = ?", ACTIVE_COURSE_ID);
        }
    }

    @Test
    @DisplayName("본문에 좌표를 실으면 컨트롤러 전에 400 COMMON-4001 로 끊긴다")
    void coordinate_in_body_is_blocked() throws Exception {
        mvc.perform(post("/api/pilgrimages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1,\"latitude\":37.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-4001"));
    }

    @Test
    @DisplayName("동시에 두 번 시작해도 순례는 한 건이다 — uk 가 막고 Service 가 재조회한다")
    void concurrent_start_creates_one_row() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        Callable<Integer> task = () -> {
            ready.countDown();
            go.await(5, TimeUnit.SECONDS);
            return mvc.perform(startRequest(ACTIVE_COURSE_ID)).andReturn().getResponse().getStatus();
        };
        try {
            Future<Integer> a = pool.submit(task);
            Future<Integer> b = pool.submit(task);
            ready.await(5, TimeUnit.SECONDS);
            go.countDown();   // 둘을 같은 순간에 풀어 SELECT→INSERT 사이를 겹치게 한다

            assertThat(a.get(10, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(b.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        } finally {
            pool.shutdownNow();
        }

        // 어느 쪽이 졌든 사용자에게는 500 이 아니라 정상 응답이 가고, 행은 하나다.
        assertThat(pilgrimageCount()).isEqualTo(1);
    }

    /* ---------------- 여권 ---------------- */

    @Test
    @DisplayName("비로그인 여권은 401 AUTH-4013")
    void passport_requires_login() throws Exception {
        mvc.perform(get("/api/passport"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH-4013"));
    }

    @Test
    @DisplayName("아직 아무것도 시작하지 않아도 코스가 보이고 칸은 5개다")
    void passport_shows_unstarted_courses() throws Exception {
        JsonNode data = passport().path("data");
        JsonNode course = firstCourse(data);

        // ★ p.user_id 조건이 WHERE 로 내려가면 이 코스가 통째로 사라진다
        assertThat(course.path("pilgrimageId").isNull()).isTrue();
        assertThat(course.path("status").isNull()).isTrue();
        assertThat(course.path("slots")).hasSize(5);
        assertThat(course.path("completedCount").asInt()).isZero();

        JsonNode slot = course.path("slots").get(0);
        assertThat(slot.path("position").asInt()).isEqualTo(1);
        assertThat(slot.path("completed").asBoolean()).isFalse();
        assertThat(slot.path("siteName").asText()).isNotBlank();
    }

    @Test
    @DisplayName("요약 4개는 저장값이 아니라 센 값이다 — ACTIVE 코스 수·자리 수와 맞는다")
    void passport_summary_is_counted_not_stored() throws Exception {
        JsonNode summary = passport().path("data").path("summary");

        int activeCourses = jdbc.queryForObject(
                "SELECT COUNT(*) FROM course WHERE status = 'ACTIVE'", Integer.class);
        int activeSlots = jdbc.queryForObject("""
                SELECT COUNT(*) FROM course_site cs
                JOIN course c ON c.course_id = cs.course_id AND c.status = 'ACTIVE'
                """, Integer.class);

        assertThat(summary.path("totalCourses").asInt()).isEqualTo(activeCourses);
        assertThat(summary.path("totalSites").asInt()).isEqualTo(activeSlots);
        assertThat(summary.path("completedCourses").asInt()).isZero();
        assertThat(summary.path("completedSites").asInt()).isZero();
    }

    @Test
    @DisplayName("시작하면 그 코스 칸에 pilgrimageId 가 채워진다")
    void passport_reflects_started_pilgrimage() throws Exception {
        long pilgrimageId = start(ACTIVE_COURSE_ID).path("data").path("pilgrimageId").asLong();

        JsonNode course = firstCourse(passport().path("data"));
        assertThat(course.path("pilgrimageId").asLong()).isEqualTo(pilgrimageId);
        assertThat(course.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(course.path("completedCount").asInt()).isZero();
        assertThat(course.path("slots")).hasSize(5);   // 시작해도 칸 수는 그대로다
    }

    @Test
    @DisplayName("코스 목록의 progress 가 여권과 같은 출처를 본다")
    void course_progress_matches_pilgrimage() throws Exception {
        long pilgrimageId = start(ACTIVE_COURSE_ID).path("data").path("pilgrimageId").asLong();

        String body = mvc.perform(get("/api/courses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode progress = om.readTree(body).path("data").path("items").get(0).path("progress");
        assertThat(progress.path("pilgrimageId").asLong()).isEqualTo(pilgrimageId);
        assertThat(progress.path("completedCount").asInt()).isZero();
        assertThat(progress.path("total").asInt()).isEqualTo(5);
    }

    /* ---------------- 제거된 경로 ---------------- */

    @Test
    @DisplayName("옛 경로는 사라졌다 — 옛 여권·코스 진행률은 404, 목록은 405")
    void removed_endpoints_are_gone() throws Exception {
        for (String url : new String[]{"/api/pilgrimages/passport", "/api/courses/1/progress"}) {
            mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("COMMON-4040"));
        }

        // 목록만 405 다 — 같은 경로에 POST 가 남아 있어 "경로는 있는데 GET 은 없다" 가 되기 때문이다.
        mvc.perform(get("/api/pilgrimages").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("COMMON-4050"));
    }

    /* ---------------- 도우미 ---------------- */

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder startRequest(long courseId) {
        return post("/api/pilgrimages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courseId\":%d}".formatted(courseId));
    }

    private JsonNode start(long courseId) throws Exception {
        String body = mvc.perform(startRequest(courseId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body);
    }

    private JsonNode passport() throws Exception {
        String body = mvc.perform(get("/api/passport")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body);
    }

    /** 시드의 ACTIVE 코스는 courseId 1 하나뿐이라 첫 권역의 첫 코스가 그것이다. */
    private JsonNode firstCourse(JsonNode data) {
        for (JsonNode region : data.path("regions")) {
            for (JsonNode course : region.path("courses")) {
                if (course.path("courseId").asLong() == ACTIVE_COURSE_ID) return course;
            }
        }
        throw new AssertionError("여권에 코스 " + ACTIVE_COURSE_ID + " 가 없다 — p.user_id 조건이 WHERE 로 내려갔는지 확인");
    }

    private int pilgrimageCount() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM pilgrimage WHERE user_id = ? AND course_id = ?",
                Integer.class, userId, ACTIVE_COURSE_ID);
    }

    private void signup() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s","nickname":"순례검증"}
                        """.formatted(EMAIL, PASSWORD))).andExpect(status().isCreated());
    }

    private String login() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body).path("data").path("accessToken").asText();
    }

    /** pilgrimage 는 FK 가 RESTRICT 라 users 보다 먼저 지운다. */
    private void cleanUp() {
        jdbc.update("""
                DELETE FROM pilgrimage WHERE user_id IN (SELECT user_id FROM users WHERE email = ?)
                """, EMAIL);
        jdbc.update("DELETE FROM users WHERE email = ?", EMAIL);
    }
}
