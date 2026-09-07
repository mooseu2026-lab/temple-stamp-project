package com.templestamp.stamp;

import com.templestamp.global.config.AppProperties;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * QR 은 사찰 현장에 물리적으로 붙어 있어 사진 한 장으로도 새어 나간다.
 * 그래서 서명·사찰·버전·만료 네 가지가 각각 독립적으로 막히는지 못 박아 둔다.
 */
class QrTokenProviderTest {

    private static final String JWT_SECRET = "and0LXNlY3JldC1mb3ItdGVzdC0zMmJ5dGVzLW1pbmltdW0h";
    private static final String QR_SECRET = "cXItc2VjcmV0LWZvci10ZXN0LTMyYnl0ZXMtbWluaW11bSEh";

    private QrTokenProvider provider() {
        return new QrTokenProvider(properties(QR_SECRET));
    }

    private AppProperties properties(String qrSecret) {
        return new AppProperties(
                new AppProperties.Jwt(JWT_SECRET, 1800, 7),
                new AppProperties.Qr(qrSecret, 300),
                new AppProperties.Cookie(false));
    }

    @Test
    @DisplayName("발급한 토큰은 같은 사찰·같은 버전에서 통과한다")
    void issuedTokenVerifies() {
        QrTokenProvider provider = provider();
        String token = provider.issue(7L, 1).token();

        assertThatCode(() -> provider.verify(token, 7L, 1)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("다른 사찰의 QR 이면 QR_SITE_MISMATCH")
    void rejectsOtherSite() {
        QrTokenProvider provider = provider();
        String token = provider.issue(7L, 1).token();

        assertThatThrownBy(() -> provider.verify(token, 8L, 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.STAMP_4004);
    }

    @Test
    @DisplayName("qr_version 을 올리면 이전에 뿌린 QR 이 전부 무효가 된다")
    void rejectsOldVersion() {
        QrTokenProvider provider = provider();
        String oldToken = provider.issue(7L, 1).token();

        assertThatThrownBy(() -> provider.verify(oldToken, 7L, 2))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.STAMP_4002);
    }

    @Test
    @DisplayName("유효 시간이 지난 토큰은 QR_TOKEN_EXPIRED")
    void rejectsExpired() {
        QrTokenProvider provider = provider();
        String token = provider.issue(7L, 1, -10).token();

        assertThatThrownBy(() -> provider.verify(token, 7L, 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.STAMP_4003);
    }

    @Test
    @DisplayName("본문을 바꿔치기한 토큰은 서명 검증에서 걸린다 — 사찰 비교까지 가지도 못한다")
    void rejectsTamperedPayload() {
        QrTokenProvider provider = provider();
        String token = provider.issue(7L, 1).token();
        String tampered = provider.issue(8L, 1).token().split("\\.")[0] + "." + token.split("\\.")[1];

        assertThatThrownBy(() -> provider.verify(tampered, 8L, 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.STAMP_4002);
    }

    @Test
    @DisplayName("다른 키로 만든 토큰은 통과하지 못한다")
    void rejectsForeignKey() {
        String token = provider().issue(7L, 1).token();
        QrTokenProvider other = new QrTokenProvider(
                properties("YW5vdGhlci1xci1zZWNyZXQtdG90YWxseS1kaWZmZXJlbnQh"));

        assertThatThrownBy(() -> other.verify(token, 7L, 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.STAMP_4002);
    }

    @Test
    @DisplayName("QR_SECRET 이 JWT_SECRET 과 같으면 기동 자체가 실패한다")
    void rejectsSharedSecret() {
        assertThatThrownBy(() -> new QrTokenProvider(properties(JWT_SECRET)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("QR_SECRET");
    }
}
