package com.templestamp.global.security;

import com.templestamp.user.UserMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authorization: Bearer 파싱 → SecurityContext 주입.
 * <p>
 * 여기서 401 을 직접 쓰지 않는다. 컨텍스트만 비우고 통과시키면, 보호 경로는 SecurityConfig 가
 * JsonAuthenticationEntryPoint 로 401 JSON 을 내보낸다. 필터는 DispatcherServlet 앞이라
 * @RestControllerAdvice 가 못 잡는 구간이고, 여기서 던지면 ApiResponse 형식이 깨진
 * 스프링 기본 에러가 나간다.
 * <p>
 * @Component 를 일부러 붙이지 않았다. 빈으로 등록하면 스프링 부트가 서블릿 필터 체인에도
 * 자동 등록해 두 번 실행된다. SecurityConfig 에서 new 로 만들어 쓴다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";
    private static final String DELETED = "DELETED";

    private final JwtProvider jwtProvider;
    private final UserMapper userMapper;

    public JwtAuthenticationFilter(JwtProvider jwtProvider, UserMapper userMapper) {
        this.jwtProvider = jwtProvider;
        this.userMapper = userMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(BEARER)) {
            try {
                AuthenticatedUser user = jwtProvider.parse(header.substring(BEARER.length()));

                // 탈퇴한 계정의 액세스 토큰은 만료 전이라도 통하면 안 된다(챕터 1 보강 §2).
                // 액세스 토큰은 무상태라 "폐기" 라는 것이 없다 — 서버가 매 요청 상태를 봐야
                // 탈퇴 즉시 막힌다. 기본키 한 번 읽는 값이고, 다른 방법(블랙리스트·짧은 만료)은
                // 저장소를 더 늘리거나 모든 사용자의 재로그인을 잦게 만든다.
                if (DELETED.equals(userMapper.findStatus(user.userId()))) {
                    SecurityContextHolder.clearContext();
                    chain.doFilter(request, response);
                    return;
                }

                var authentication = new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));

                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (JwtException | IllegalArgumentException e) {
                // 금고만 비우고 조용히 통과시킨다. 401 작성은 EntryPoint 담당.
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
