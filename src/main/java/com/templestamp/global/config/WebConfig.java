package com.templestamp.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 공통 설정. 인증 사용자 주입은 스프링 시큐리티의 @AuthenticationPrincipal 을 그대로 쓰므로
 * 별도 ArgumentResolver 를 두지 않는다.
 * <p>
 * 언어 결정은 여기 있다가 {@code global.web.LocaleUtil} 로 옮겼다. 스프링 설정이 아니라 순수 계산이라
 * @Configuration 안에 있을 이유가 없었고, 쿼리 locale 까지 보게 되면서 규칙이 한 겹 늘었기 때문이다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
}
