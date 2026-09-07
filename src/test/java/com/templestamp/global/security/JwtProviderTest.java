package com.templestamp.global.security;

import com.templestamp.global.config.AppProperties;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 시크릿은 "값"만이 아니라 "해석 방식"까지가 스펙이다.
 * Base64 로 해독해 키를 만드는지, 짧은 키를 기동에서 잡는지, 두 키가 분리돼 있는지를 못 박는다.
 */
class JwtProviderTest {

    private static final String JWT_SECRET = "and0LXNlY3JldC1mb3ItdGVzdC0zMmJ5dGVzLW1pbmltdW0h";
    private static final String QR_SECRET = "cXItc2VjcmV0LWZvci10ZXN0LTMyYnl0ZXMtbWluaW11bSEh";

    private AppProperties properties(String jwtSecret, String qrSecret) {
        return new AppProperties(
                new AppProperties.Jwt(jwtSecret, 1800, 7),
                new AppProperties.Qr(qrSecret, 300),
                new AppProperties.Cookie(false));
    }

    private JwtProvider provider() {
        return new JwtProvider(properties(JWT_SECRET, QR_SECRET));
    }

    @Test
    @DisplayName("발급한 액세스 토큰에서 userId·email·role 이 그대로 돌아온다")
    void roundTrip() {
        JwtProvider provider = provider();
        String token = provider.createAccessToken(17L, "user@example.com", "USER");

        AuthenticatedUser user = provider.parse(token);

        assertThat(user.userId()).isEqualTo(17L);
        assertThat(user.email()).isEqualTo("user@example.com");
        assertThat(user.role()).isEqualTo("USER");
        assertThat(user.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("role 이 null 이어도 isAdmin() 이 NPE 없이 false 다")
    void adminCheckIsNullSafe() {
        assertThat(new AuthenticatedUser(1L, "a@b.c", null).isAdmin()).isFalse();
        assertThat(new AuthenticatedUser(1L, "a@b.c", "ADMIN").isAdmin()).isTrue();
    }

    @Test
    @DisplayName("expiresIn 은 설정한 액세스 유효시간(초)을 그대로 돌려준다")
    void expiresInMatchesConfig() {
        assertThat(provider().getAccessExpiresInSeconds()).isEqualTo(1800L);
    }

    @Test
    @DisplayName("서명이 다른 토큰은 JwtException")
    void rejectsForeignSignature() {
        String token = provider().createAccessToken(1L, "a@b.c", "USER");
        JwtProvider other = new JwtProvider(
                properties("YW5vdGhlci1qd3Qtc2VjcmV0LTMyYnl0ZXMtbWluaW11bSEh", QR_SECRET));

        assertThatThrownBy(() -> other.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Base64 로 32바이트가 안 되는 시크릿은 기동에서 막는다")
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new JwtProvider(properties("c2hvcnQ=", QR_SECRET)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32바이트");
    }

    @Test
    @DisplayName("JWT_SECRET 과 QR_SECRET 이 같으면 기동에서 막는다")
    void rejectsSharedSecret() {
        assertThatThrownBy(() -> new JwtProvider(properties(JWT_SECRET, JWT_SECRET)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("QR_SECRET");
    }
}
