package com.templestamp.admin.dto;

import java.time.LocalDateTime;

/**
 * 사찰 QR 발급·재발급 결과를 내려주는 DTO Record입니다.
 * 재발급하면 이전 토큰은 즉시 무효 — site.qr_version 을 올려 서명 자체를 갈아 치우기 때문입니다.
 * qrImageBase64 는 data URI 없이 순수 base64 PNG. 화면에서 data:image/png;base64, 를 붙여 쓴다.
 * [사용 위치] AdminSiteController — QrTokenProvider 가 서명한 토큰을 조립해 반환
 */
public record QrIssueResponse(
        Long siteId,
        Long courseSiteId,
        String siteName,
        String qrToken,             // 현장 인쇄용. 이 문자열로 QR 이미지를 만든다
        String qrImageBase64,
        Integer qrVersion,
        LocalDateTime issuedAt,
        LocalDateTime expiresAt
) {
}
