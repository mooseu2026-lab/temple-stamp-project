package com.templestamp.global.security;

import com.templestamp.global.web.RequestIdFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

/**
 * CORS preflight(챕터 11 결정 G · 리뷰 3-6).
 * <p>
 * 셋을 고정한다 — <b>허용 헤더에 {@code X-Request-Id}</b>, <b>응답에서 그 헤더 노출</b>,
 * <b>preflight 캐시 3600초</b>.
 * <p>
 * 이 셋이 왜 한 검사인가. 우리 프론트는 모든 요청에 요청 id 를 실어 보내고, 오류가 나면
 * 응답의 같은 헤더를 읽어 사용자에게 보여 준다. 허용 목록에 없으면 <b>브라우저가 요청 자체를 보내지 않고</b>,
 * 노출 목록에 없으면 응답은 왔는데 자바스크립트가 그 헤더를 읽지 못한다. 둘 다 조용한 실패다 —
 * 서버 로그에는 아무 흔적이 없다.
 * <p>
 * 와일드카드를 쓸 수 없는 것도 여기 걸려 있다. {@code allowCredentials(true)} 라서
 * {@code Access-Control-Allow-Origin} 에 {@code *} 를 실을 수 없다 — 도메인을 명시해야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CorsTest {

    private static final String PROBE = "/api/courses";

    @Autowired MockMvc mvc;

    /** 설정에 적힌 첫 오리진. 값을 테스트에 박으면 설정을 바꾼 날 이 검사가 거짓으로 통과한다. */
    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    private String allowedOrigin() {
        return allowedOrigins.split(",")[0].trim();
    }

    @Test
    @DisplayName("preflight 는 x-request-id 를 허용하고, 응답에서 그 헤더를 노출하고, 3600초 캐시한다")
    void preflight_allows_exposes_and_caches() throws Exception {
        var response = mvc.perform(options(PROBE)
                        .header(HttpHeaders.ORIGIN, allowedOrigin())
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, RequestIdFilter.HEADER))
                .andReturn().getResponse();

        assertThat(response.getStatus()).as("preflight 는 통과해야 한다").isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
                .as("없으면 브라우저가 본 요청을 아예 보내지 않는다")
                .containsIgnoringCase(RequestIdFilter.HEADER);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
                .as("없으면 응답은 오는데 자바스크립트가 요청 id 를 읽지 못한다")
                .containsIgnoringCase(RequestIdFilter.HEADER);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_MAX_AGE)).isEqualTo("3600");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .as("자격증명을 허용하므로 * 가 아니라 도메인이어야 한다")
                .isEqualTo(allowedOrigin());
    }

    @Test
    @DisplayName("허용 목록에 없는 오리진의 preflight 는 통과하지 못한다")
    void unknown_origin_is_rejected() throws Exception {
        var response = mvc.perform(options(PROBE)
                        .header(HttpHeaders.ORIGIN, "https://not-our-site.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isNotEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }
}
