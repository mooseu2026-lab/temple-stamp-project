package com.templestamp.stamp;

import com.templestamp.global.config.AppProperties;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * 사찰 QR 토큰 발급/검증.
 * <p>
 * ★ app.qr.secret 은 app.jwt.secret 과 반드시 다른 값이다. QR 은 사찰 현장에 물리적으로
 * 붙어 있어 사진 한 장으로도 새어 나간다. 로그인 키와 같은 키를 쓰면 그 순간
 * QR 하나로 액세스 토큰을 위조할 수 있게 된다.
 * <p>
 * 토큰에 사찰의 qr_version 을 함께 서명한다. QR 이 유출되면 site.qr_version 을 +1 하는 것만으로
 * 그 사찰에 뿌려 둔 QR 이 한 번에 무효가 된다 — 종이를 회수하기 전에 피해를 끊을 수 있다.
 * <p>
 * 토큰 모양: {@code base64url(payload).base64url(hmac)} — payload 는
 * {@code v1|siteId|qrVersion|issuedAtEpoch|expiresAtEpoch|nonce} 다. JWT 를 쓰지 않는 이유는
 * QR 로 찍을 문자열을 짧게 유지해야 인식률이 나오기 때문이다.
 */
@Slf4j
@Component
public class QrTokenProvider {

    private static final String VERSION = "v1";
    private static final String SEPARATOR = ".";
    private static final String FIELD_DELIMITER = "|";
    private static final int FIELD_COUNT = 6;

    private final byte[] secret;
    private final long defaultValiditySeconds;

    public QrTokenProvider(AppProperties properties) {
        String qrSecret = properties.qr().secret();
        if (qrSecret.equals(properties.jwt().secret())) {
            throw new IllegalStateException("QR_SECRET 은 JWT_SECRET 과 달라야 합니다.");
        }
        this.secret = qrSecret.getBytes(StandardCharsets.UTF_8);
        this.defaultValiditySeconds = properties.qr().tokenSeconds();
    }

    /** 발급 결과. 관리자 화면이 이 값을 QR 이미지로 그린다. */
    public record QrToken(String token, Instant issuedAt, Instant expiresAt) {
    }

    public QrToken issue(Long siteId, int qrVersion) {
        return issue(siteId, qrVersion, defaultValiditySeconds);
    }

    /**
     * @param validitySeconds 상설 안내판처럼 오래 붙여 두는 QR 은 길게, 회전 표시기는 짧게 준다.
     */
    public QrToken issue(Long siteId, int qrVersion, long validitySeconds) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(validitySeconds);

        String payload = String.join(FIELD_DELIMITER,
                VERSION,
                String.valueOf(siteId),
                String.valueOf(qrVersion),
                String.valueOf(now.getEpochSecond()),
                String.valueOf(expiresAt.getEpochSecond()),
                UUID.randomUUID().toString().replace("-", "").substring(0, 12));

        String encodedPayload = encode(payload.getBytes(StandardCharsets.UTF_8));
        String signature = encode(hmac(encodedPayload));

        return new QrToken(encodedPayload + SEPARATOR + signature, now, expiresAt);
    }

    /**
     * 서명·사찰·버전·만료를 모두 확인한다. 어긋난 이유마다 다른 코드를 던진다 —
     * 사용자에게 "다시 스캔" 과 "여기 QR 이 아님" 은 완전히 다른 안내이기 때문이다.
     *
     * @param expectedQrVersion 현재 site.qr_version. 이보다 낮은 QR 은 무효화된 것이다.
     */
    public void verify(String token, Long expectedSiteId, int expectedQrVersion) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.STAMP_4002);
        }

        int index = token.indexOf(SEPARATOR);
        if (index <= 0 || index == token.length() - 1) {
            throw new BusinessException(ErrorCode.STAMP_4002);
        }

        String encodedPayload = token.substring(0, index);
        String signature = token.substring(index + 1);

        // 서명부터 본다. 위조된 payload 를 먼저 파싱하면 엉뚱한 예외가 튄다.
        if (!MessageDigest.isEqual(
                encode(hmac(encodedPayload)).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.STAMP_4002);
        }

        String[] fields = decode(encodedPayload).split("\\" + FIELD_DELIMITER);
        if (fields.length != FIELD_COUNT || !VERSION.equals(fields[0])) {
            throw new BusinessException(ErrorCode.STAMP_4002);
        }

        long siteId;
        int qrVersion;
        long expiresAt;
        try {
            siteId = Long.parseLong(fields[1]);
            qrVersion = Integer.parseInt(fields[2]);
            expiresAt = Long.parseLong(fields[4]);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.STAMP_4002);
        }

        if (!expectedSiteId.equals(siteId)) {
            throw new BusinessException(ErrorCode.STAMP_4004);
        }
        if (qrVersion != expectedQrVersion) {
            throw new BusinessException(ErrorCode.STAMP_4002,
                    "교체된 QR 입니다. 사찰에 새로 붙은 QR 을 스캔해 주세요.");
        }
        if (Instant.now().getEpochSecond() > expiresAt) {
            throw new BusinessException(ErrorCode.STAMP_4003);
        }
    }

    private byte[] hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("QR 서명 생성 실패", e);
        }
    }

    private String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String decode(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.STAMP_4002);
        }
    }
}
