package com.templestamp.stamp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 인증 2단계에서 스캔한 QR 토큰 문자열을 전달받는 DTO Record입니다.
 * 서명·만료·사찰 일치 검증은 전부 Service 가 합니다.
 * [사용 위치] StampController — @RequestBody @Valid
 */
public record QrVerifyRequest(

        @NotBlank(message = "QR 토큰이 비어 있습니다.")
        @Size(max = 512, message = "QR 토큰 형식이 올바르지 않습니다.")
        String qrToken
) {
}
