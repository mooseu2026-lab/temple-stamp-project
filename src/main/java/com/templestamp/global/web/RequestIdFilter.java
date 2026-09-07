package com.templestamp.global.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 요청 하나에 식별자 하나. 클라이언트가 X-Request-Id 를 주면 그대로 쓰고, 없으면 만든다.
 * 로그 MDC 와 응답 헤더에 같은 값이 실리므로 사용자 문의 → 로그 추적이 한 번에 된다.
 */
@Order(-110)
@Component
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestId = request.getHeader(HEADER);
        if (!StringUtils.hasText(requestId) || requestId.length() > 64) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }

        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
