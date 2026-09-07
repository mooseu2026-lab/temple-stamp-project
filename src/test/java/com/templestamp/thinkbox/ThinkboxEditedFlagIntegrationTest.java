package com.templestamp.thinkbox;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * `thinkbox.is_edited` 가 실제로 켜지는지 확인한다.
 * <p>
 * MySQL 은 {@code SET} 절을 왼쪽부터 평가하고 뒤에 오는 식은 앞에서 이미 바뀐 값을 본다.
 * {@code body} 를 먼저 대입하면 뒤의 {@code CASE WHEN body <> #{body}} 가 항상 거짓이라
 * <b>is_edited 가 영원히 0</b> 이었다. 순서를 뒤집어 고쳤고, 이 테스트가 그 자리를 지킨다.
 * <p>
 * 화면만 봐서는 "아직 아무도 수정을 안 했나 보다" 로 읽혀 오래 모르는 종류라,
 * 응답이 아니라 <b>DB 컬럼을 직접</b> 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ThinkboxEditedFlagIntegrationTest {

    private static final String EMAIL = "thinkbox-it@test.com";
    private static final String PASSWORD = "Check!2026";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
        signup();
        token = login();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("본문을 바꾸면 is_edited 가 1 이 된다")
    void editing_body_sets_flag() throws Exception {
        long id = create("처음 쓴 다짐 문장입니다.");
        assertThat(isEdited(id)).isZero();

        patchThinkbox(id, """
                {"content":"고쳐 쓴 다짐 문장입니다."}""");

        assertThat(isEdited(id)).isOne();
        assertThat(body(id)).isEqualTo("고쳐 쓴 다짐 문장입니다.");
    }

    @Test
    @DisplayName("공개 설정만 바꾸면 is_edited 는 0 그대로다 — 수정이 아니다")
    void changing_visibility_only_keeps_flag_zero() throws Exception {
        long id = create("처음 쓴 다짐 문장입니다.");
        agreeEbookPublic();   // 공개로 바꾸려면 전자책 수록 동의가 필요하다

        // content 를 보내지 않으므로 Service 가 기존 본문을 그대로 다시 넣는다.
        // 본문이 같으니 CASE 가 거짓이어야 하고, is_edited 는 0 이어야 한다.
        patchThinkbox(id, """
                {"isPrivate":false}""");

        assertThat(isPrivate(id)).isFalse();   // 공개 설정은 실제로 바뀌었다
        assertThat(isEdited(id)).isZero();     // 그런데 "수정됨" 은 아니다
    }

    @Test
    @DisplayName("같은 본문으로 다시 저장해도 is_edited 는 0 이다")
    void resaving_same_body_keeps_flag_zero() throws Exception {
        long id = create("처음 쓴 다짐 문장입니다.");

        patchThinkbox(id, """
                {"content":"처음 쓴 다짐 문장입니다."}""");

        assertThat(isEdited(id)).isZero();
    }

    /* ---------------- 도우미 ---------------- */

    private long create(String content) throws Exception {
        String res = mvc.perform(post("/api/thinkbox")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"%s\"}".formatted(content)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(res).path("data").asLong();
    }

    private void patchThinkbox(long id, String body) throws Exception {
        mvc.perform(patch("/api/thinkbox/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private void agreeEbookPublic() throws Exception {
        mvc.perform(post("/api/users/me/agreements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"agreementType":"EBOOK_PUBLIC","version":1}]"""))
                .andExpect(status().isOk());
    }

    private int isEdited(long id) {
        return jdbc.queryForObject(
                "SELECT is_edited FROM thinkbox WHERE thinkbox_id = ?", Integer.class, id);
    }

    private boolean isPrivate(long id) {
        return jdbc.queryForObject(
                "SELECT is_private FROM thinkbox WHERE thinkbox_id = ?", Boolean.class, id);
    }

    private String body(long id) {
        return jdbc.queryForObject(
                "SELECT body FROM thinkbox WHERE thinkbox_id = ?", String.class, id);
    }

    private void signup() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s","nickname":"생각검증"}
                        """.formatted(EMAIL, PASSWORD))).andExpect(status().isCreated());
    }

    private String login() throws Exception {
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(res).path("data").path("accessToken").asText();
    }

    /** thinkbox·user_agreement 가 users 를 RESTRICT 로 잡으므로 먼저 지운다. */
    private void cleanUp() {
        jdbc.update("DELETE FROM thinkbox WHERE user_id IN (SELECT user_id FROM users WHERE email = ?)", EMAIL);
        jdbc.update("DELETE FROM user_agreement WHERE user_id IN (SELECT user_id FROM users WHERE email = ?)", EMAIL);
        jdbc.update("DELETE FROM users WHERE email = ?", EMAIL);
    }
}
