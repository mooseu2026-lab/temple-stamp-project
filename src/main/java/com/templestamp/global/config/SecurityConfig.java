package com.templestamp.global.config;

import com.templestamp.global.security.JsonAccessDeniedHandler;
import com.templestamp.global.security.JsonAuthenticationEntryPoint;
import com.templestamp.global.security.JwtAuthenticationFilter;
import com.templestamp.global.security.JwtProvider;
import com.templestamp.global.web.RequestIdFilter;
import com.templestamp.user.UserMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * STATELESS + JWT. 공개 경로는 여기 permitAll 목록이 유일한 기준.
 * 401 → JsonAuthenticationEntryPoint / 403 → JsonAccessDeniedHandler (ApiResponse JSON).
 */
@Configuration                                        // 이 안에 @Bean 메서드가 있다는 표식
@EnableWebSecurity                                    // 스프링 시큐리티를 켠다
public class SecurityConfig {

    private final JwtProvider jwtProvider;            // 필터를 만들 때 넘겨줄 발급소
    private final JsonAuthenticationEntryPoint entryPoint;   // 401 안내판
    private final JsonAccessDeniedHandler deniedHandler;     // 403 안내판
    private final CorsProperties corsProperties;             // 허용 오리진 목록
    private final UserMapper userMapper;                     // 탈퇴한 계정인지 필터가 매 요청 확인

    public SecurityConfig(JwtProvider jwtProvider,    // 다섯 다 빈이라 스프링이 자동 주입
                          JsonAuthenticationEntryPoint entryPoint,
                          JsonAccessDeniedHandler deniedHandler,
                          CorsProperties corsProperties,
                          UserMapper userMapper) {
        this.jwtProvider = jwtProvider;
        this.entryPoint = entryPoint;
        this.deniedHandler = deniedHandler;
        this.corsProperties = corsProperties;
        this.userMapper = userMapper;
    }

    @Bean                                             // 서버 켜질 때 딱 한 번 실행되어 경비 배치도를 완성
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            // CSRF 는 "브라우저가 쿠키를 자동 전송하는 성질" 을 악용하는 공격.
            // 인증을 헤더의 Bearer 로 하므로 자동 전송되지 않음 → 표면 없음.
            // ※ 리프레시는 쿠키라 표면이 있는데, 그건 SameSite=Strict 로 막는다(AuthController)

            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // 프론트가 다른 포트에서 API 를 부를 수 있게 허용. 규칙은 아래 메서드에

            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 세션을 아예 만들지 않는다.
            //   옛날 방식: 서버 메모리에 "이 세션ID는 3번 유저" 기록 → 서버가 상태를 기억
            //   우리 방식: 아무것도 기억 안 하고 매 요청마다 토큰만 보고 판단
            //   → 서버를 여러 대로 늘려도 세션 공유 문제가 없다

            .formLogin(f -> f.disable())              // 스프링 기본 로그인 화면(HTML 폼) 끔. 우리는 JSON API
            .httpBasic(b -> b.disable())              // 브라우저 팝업으로 아이디/비번 묻는 방식도 끔

            .exceptionHandling(e -> e
                    .authenticationEntryPoint(entryPoint)     // 인증 안 됨 → 401 JSON
                    .accessDeniedHandler(deniedHandler))      // 권한 부족 → 403 JSON

            .authorizeHttpRequests(auth -> auth
                    // ↓ 위에서부터 순서대로 검사하고, 먼저 걸리는 규칙이 이긴다
                    .requestMatchers("/api/auth/**").permitAll()
                    // 회원가입·로그인·재발급·로그아웃 — 토큰이 있을 수 없거나 만료됐을 수 있으니 열어둔다
                    // ※ 로그아웃은 비로그인으로 들어와도 쿠키만 지우고 200 을 돌려준다(AuthController)

                    .requestMatchers(HttpMethod.GET,
                            // ※ /api/regions 는 하위 경로가 없어 정확히 일치시킨다.
                            //   /** 로 두면 훗날 /api/regions/{id}/... 가 생겼을 때 의도 없이 공개된다.
                            //   대신 스프링 부트 3 은 trailing slash 매칭이 꺼져 있어 "/api/regions/" 는
                            //   여기 안 걸리고 anyRequest().authenticated() 로 떨어진다(→ 401).
                            "/api/regions", "/api/courses/**", "/api/sites/**",
                            "/api/verses/**", "/api/meditations/**", "/api/content/**").permitAll()
                    // ※ GET 으로 제한한 것이 핵심 — 조회는 누구나, 수정(POST/PUT/DELETE)은 로그인 필요

                    .requestMatchers(HttpMethod.GET, "/api/certificates/verify/**").permitAll()
                    // 인증서 진위 확인은 제3자(수령인·기관)가 하는 것이라 로그인이 있을 수 없다

                    .requestMatchers("/error").permitAll()
                    .requestMatchers(HttpMethod.GET, "/health").permitAll()
                    // 기동·배포 확인용. actuator 는 health 하나만 노출하고 base-path 를 / 로 두었다
                    // (application.yml 의 management.*). 열지 않으면 로드밸런서가 401 을 받는다.
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    // preflight 는 인증 헤더 없이 온다. 막으면 본 요청이 시작조차 못 한다

                    .requestMatchers("/api/editor/**").hasAnyRole("EDITOR", "ADMIN")
                    // 편집자는 원고를 쓰고 내지만 심사는 못 한다. 관리자는 둘 다 할 수 있어 함께 넣는다
                    // — 다만 자기 원고를 자기가 승인하는 것은 서비스가 막는다(4-eyes, MS-4030).

                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    // hasRole("ADMIN") 은 내부적으로 ROLE_ADMIN 을 찾는다
                    // → 필터에서 "ROLE_" 접두사를 붙인 이유

                    .anyRequest().authenticated())
                    // 위에 안 걸린 나머지 전부 로그인 필요
                    // ※ 반드시 맨 마지막. 위에 있으면 그 아래 규칙이 절대 안 걸린다

            .addFilterBefore(new JwtAuthenticationFilter(jwtProvider, userMapper),
                    UsernamePasswordAuthenticationFilter.class);
            // "우리 문지기를 스프링 기본 폼 로그인 필터보다 앞에 세워라"
            // ※ new 로 직접 생성 — @Component 가 아니라서 서블릿 필터 체인에 중복 등록되지 않는다
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
        // BCrypt:
        //   ① 단방향 — 로그인은 "복호화 비교" 가 아니라 "다시 해시해서 대조"
        //   ② 매번 다른 결과 — 랜덤 salt 가 해시 문자열 안에 함께 들어 있어 matches() 가 된다
        //   ③ 일부러 느림 — 무차별 대입을 어렵게 하려고
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.allowedOrigins());
        // ※ 목록을 설정으로 뺀 이유 — 휴대폰에서 붙을 때 PC 사설 IP 를 넣어야 하는데,
        //   코드에 박아 두면 그때마다 다시 빌드해야 한다. .env 의 FRONTEND_URL 로 바꾼다

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept-Language",
                RequestIdFilter.HEADER));
        // ★ X-Request-Id 를 허용 목록에 넣지 않으면 브라우저가 preflight 에서 막는다.
        //   서버는 그 헤더를 읽을 준비가 돼 있는데(RequestIdFilter) 요청이 오지를 못했다(리뷰 3-2).

        config.setExposedHeaders(List.of(RequestIdFilter.HEADER));
        // ★ 응답 헤더도 따로 열어 줘야 자바스크립트가 읽는다. 오류 토스트에 붙일 문의 번호다.

        config.setAllowCredentials(true);
        // ★ 쿠키를 크로스 오리진으로 주고받게 허용. false 면 리프레시 쿠키가 실려 가지 않는다
        // ※ 이게 true 면 오리진에 "*" 를 쓸 수 없다 — 브라우저가 거부. 그래서 목록을 명시

        config.setMaxAge(3600L);
        // ★ preflight 를 1시간 캐시한다. 없으면 요청마다 OPTIONS 가 한 번씩 더 간다(리뷰 3-3).

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
