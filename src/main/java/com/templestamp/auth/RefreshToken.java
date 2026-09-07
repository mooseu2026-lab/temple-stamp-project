package com.templestamp.auth;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * refresh_token 테이블 도메인 클래스.
 * 원문은 어디에도 남기지 않고 SHA-256 16진 해시(64자)만 저장한다.
 * 재발급 때 쿠키로 들어온 토큰을 같은 방식으로 해시해서 이 값과 비교한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    private Long refreshTokenId;
    private Long userId;
    private String tokenHash;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private LocalDateTime createdAt;

    public boolean isRevoked() { return revokedAt != null; }

    public boolean isExpired() {
        return expiresAt == null || expiresAt.isBefore(LocalDateTime.now());
    }
}
