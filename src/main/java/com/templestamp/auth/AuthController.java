package com.templestamp.auth;

import com.templestamp.auth.dto.LoginRequest;
import com.templestamp.auth.dto.LoginResponse;
import com.templestamp.auth.dto.SignupRequest;
import com.templestamp.global.config.AppProperties;
import com.templestamp.global.response.ApiResponse;
import com.templestamp.global.security.AuthenticatedUser;
import com.templestamp.user.dto.UserResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 인증 엔드포인트.
 * <p>
 * 리프레시 토큰은 응답 본문에 실리지 않는다. HttpOnly 쿠키로만 오가므로 자바스크립트가
 * 읽을 수 없고, 그래서 XSS 로 토큰이 통째로 빠져나가지 않는다.
 * <p>
 * 쿠키가 자동 전송된다는 점 때문에 CSRF 표면이 생기는데, 그건 SameSite=Strict 로 막는다 —
 * "이 쿠키는 우리 사이트에서 시작된 요청에만 붙여라" 는 뜻이다. path 도 /api/auth 로 좁혀 두어
 * 일반 API 요청에는 아예 실리지 않는다.
 */
@Validated
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_COOKIE = "refreshToken";
    /** 이 경로에서만 쿠키를 보낸다. 재발급·로그아웃 외에는 실리지 않는다. */
    private static final String REFRESH_COOKIE_PATH = "/api/auth";

    private final AuthService authService;
    private final AppProperties appProperties;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.ok(authService.signup(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletResponse response) {
        AuthService.IssuedTokens issued = authService.login(request);
        setRefreshCookie(response, issued.refreshToken(), issued.refreshDays());
        return ApiResponse.ok(issued.body());
    }

    /** 쿠키의 리프레시 토큰으로 새 쌍을 받는다. 쓰인 토큰은 즉시 폐기된다(회전). */
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(
            @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        AuthService.IssuedTokens issued = authService.refresh(refreshToken);
        setRefreshCookie(response, issued.refreshToken(), issued.refreshDays());
        return ApiResponse.ok(issued.body());
    }

    /**
     * 로그아웃. 액세스 토큰이 이미 만료돼 비로그인 상태로 들어와도 실패시키지 않는다 —
     * 그때야말로 사용자가 로그아웃을 누르는 상황이고, 여기서 401 을 주면 쿠키가 남는다.
     * 토큰이 있으면 그 사용자의 세션을 전부 끊고, 없으면 쿠키만 지운다.
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal AuthenticatedUser user,
                                    @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken,
                                    HttpServletResponse response) {
        authService.logout(user == null ? null : user.userId(), refreshToken);
        clearRefreshCookie(response);
        return ApiResponse.ok();
    }

    /* ---------------- 쿠키 ---------------- */

    private void setRefreshCookie(HttpServletResponse response, String token, int days) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                buildCookie(token, Duration.ofDays(days)).toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie("", Duration.ZERO).toString());
    }

    /**
     * SameSite=Strict.
     * <p>
     * 포트가 달라도(5173 ↔ 8080) 같은 사이트로 본다 — SameSite 판정은 등록 가능 도메인 기준이고
     * 포트는 보지 않는다. 그래서 로컬 개발에서도 쿠키가 정상적으로 실린다.
     * 프런트와 API 가 아예 다른 도메인에 놓이는 배포에서만 None + Secure 로 바꿔야 하고,
     * 그때는 CSRF 방어를 따로 세워야 한다.
     */
    private ResponseCookie buildCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(appProperties.cookie().secure())
                .path(REFRESH_COOKIE_PATH)
                .maxAge(maxAge)
                .sameSite("Strict")
                .build();
    }
}
