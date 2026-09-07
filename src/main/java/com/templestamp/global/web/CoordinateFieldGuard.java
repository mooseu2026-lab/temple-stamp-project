package com.templestamp.global.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.global.error.ErrorResponse;
import com.templestamp.global.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 좌표 필드 반입 차단.
 * <p>
 * 사용자 위치는 스탬프 GPS 확인 한 곳에서만 받는다. 그 외 API 가 좌표를 같이 받기 시작하면
 * 위치 이력이 여기저기 쌓이고, 어느 순간 위치기반서비스 동의 범위 밖으로 새어 나간다.
 * 그래서 화이트리스트에 없는 경로의 JSON 본문에 latitude/longitude/lat/lng 가 보이면
 * 컨트롤러에 닿기 전에 400 COMMON-4001 로 끊는다.
 * <p>
 * 검사는 중첩 객체/배열까지 전부 내려간다. 필드명 비교는 대소문자를 무시한다.
 */
@Slf4j
@Order(-105)
@Component
@RequiredArgsConstructor
public class CoordinateFieldGuard extends OncePerRequestFilter {

    /** 이 이름들은 어떤 깊이에 있든 거부한다. */
    private static final Set<String> BANNED_FIELDS = Set.of("latitude", "longitude", "lat", "lng");

    /**
     * 좌표를 받는 유일한 경로 — 관리자 사찰 등록.
     * 사찰 좌표는 시설의 고정 위치라 개인 위치정보가 아니다. 사용자 경로는 예외 없이 전부 막힌다:
     * GPS 인증조차 앱이 거리를 계산해 판정 결과만 보내므로 좌표를 실을 이유가 없다.
     */
    private static final List<String> ALLOWED_PATTERNS = List.of(
            "/api/admin/sites/**"
    );

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        boolean hasBody = "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
        if (!hasBody) {
            return true;
        }
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).contains("json")) {
            return true;
        }
        String path = request.getRequestURI();
        return ALLOWED_PATTERNS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        CachedBodyRequest cached = new CachedBodyRequest(request);
        byte[] body = cached.getBody();

        if (body.length > 0) {
            String violated = findBannedField(body);
            if (violated != null) {
                log.warn("좌표 필드 반입 차단: path={}, field={}", request.getRequestURI(), violated);
                reject(response, violated);
                return;
            }
        }

        filterChain.doFilter(cached, response);
    }

    private String findBannedField(byte[] body) {
        try {
            return scan(objectMapper.readTree(body));
        } catch (IOException e) {
            // 본문이 JSON 이 아니면 여기서 판단하지 않는다. HttpMessageNotReadableException 으로 넘긴다.
            return null;
        }
    }

    private String scan(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                if (BANNED_FIELDS.contains(name.toLowerCase(Locale.ROOT))) {
                    return name;
                }
                String nested = scan(node.get(name));
                if (nested != null) {
                    return nested;
                }
            }
            return null;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                String nested = scan(child);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private void reject(HttpServletResponse response, String field) throws IOException {
        ErrorCode errorCode = ErrorCode.COMMON_4001;
        response.setStatus(errorCode.status());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponse error = ErrorResponse.ofValidation(errorCode.code(), errorCode.message(), List.of(
                new ErrorResponse.FieldError(field, "이 API 는 좌표를 받지 않습니다.")));
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(error));
    }
}
