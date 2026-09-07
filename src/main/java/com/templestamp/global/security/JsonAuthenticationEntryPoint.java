package com.templestamp.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.global.error.ErrorResponse;
import com.templestamp.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 인증 실패(401)를 ApiResponse JSON 으로. 필터 구간이라 GlobalExceptionHandler 가 못 잡는 자리 담당 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    /**
     * new ObjectMapper() 를 안 쓰는 이유 — 스프링 기본 매퍼에는 JavaTimeModule 이 들어 있어
     * OffsetDateTime(ApiResponse.timestamp)을 제대로 직렬화한다. 직접 만들면 그 설정이 빠져 깨진다.
     */
    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // authException 을 쓰지 않는 것은 의도적이다. 실패 이유를 자세히 알려주면 공격자에게 힌트가 된다.
        ErrorCode ec = ErrorCode.AUTH_4013;

        response.setStatus(ec.status());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.fail(ErrorResponse.of(ec.code(), ec.message()))));
    }
}
