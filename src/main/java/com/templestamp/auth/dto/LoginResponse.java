package com.templestamp.auth.dto;

import com.templestamp.user.dto.UserResponse;

/**
 * 로그인 성공 시 발급된 액세스 토큰과 사용자 정보를 함께 내려주는 DTO Record입니다. (05_API명세 1-2)
 * expiresIn(초)이 있어야 프론트가 만료 전에 미리 refresh 를 칠 수 있습니다 — 없으면 매번 401 후 재발급.
 * refresh 토큰은 본문이 아니라 HttpOnly 쿠키로 나가므로 이 DTO 에 없습니다.
 * [사용 위치] AuthService 가 조립(expiresIn 은 JwtProvider 설정값) → AuthController 반환
 */
public record LoginResponse(
        String accessToken,
        String tokenType,           // 항상 "Bearer"
        long expiresIn,             // 액세스 토큰 유효 시간(초). 예: 1800
        UserResponse user
) {
    public static LoginResponse of(String accessToken, long expiresIn, UserResponse user) {
        return new LoginResponse(accessToken, "Bearer", expiresIn, user);
    }
}
