package com.templestamp.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.global.error.ErrorResponse;
import com.templestamp.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 권한 부족(403 — 예: USER 가 /api/admin/** 접근)을 ApiResponse JSON 으로.
 * <p>
 * EntryPoint(401)와 나눠 둔 이유: 401 은 "네가 누군지 모르겠다", 403 은 "누군지 알지만 안 된다" 다.
 * 클라이언트가 취할 행동이 완전히 다르다 — 앞은 로그인 화면, 뒤는 안내 문구.
 */
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JsonAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        ErrorCode ec = ErrorCode.AUTH_4032;

        response.setStatus(ec.status());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.fail(ErrorResponse.of(ec.code(), ec.message()))));
    }
}
