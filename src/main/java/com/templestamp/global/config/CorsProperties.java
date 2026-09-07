package com.templestamp.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * cors.* — 허용 오리진 목록.
 * <p>
 * 쿠키(리프레시 토큰)를 주고받아야 해서 allowCredentials=true 이고, 그러면 오리진에 "*" 를
 * 쓸 수 없다(브라우저가 거부). 그래서 목록으로 관리한다. 휴대폰에서 붙을 때는
 * .env 의 FRONTEND_URL 을 PC 사설 IP 로 바꾼다.
 */
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(List<String> allowedOrigins) {
}
