package com.templestamp.certificate.dto;

import java.time.LocalDateTime;

/**
 * 내가 받은 인증서 한 건을 내려주는 DTO Record입니다.
 * 제3자가 진위를 확인할 수 있는 URL 을 함께 담습니다.
 * 회수된 인증서도 목록에 남습니다 — 사라지면 "왜 없어졌는지" 를 사용자가 알 길이 없습니다.
 * [사용 위치] CertificateService 가 CertificateRow + 검증 URL 조립 → Controller 반환
 */
public record CertificateResponse(
        Long certificateId,
        String serialNo,
        String certType,
        String status,              // VALID / REVOKED
        String courseName,
        LocalDateTime issuedAt,
        LocalDateTime revokedAt,    // 유효하면 null
        String verifyUrl,           // {base}/api/certificates/verify/{serialNo}

        // ── 챕터 9 §3 : 인증서 PDF. VALID 일 때만 붙고 REVOKED 면 null(에러가 아니다) ──
        String downloadUrl
) {
    /** 목록용 — 링크는 붙이지 않는다. 목록마다 서명을 만들면 쓰지도 않을 URL 을 여러 개 찍는다. */
    public static CertificateResponse of(CertificateRow row, String verifyUrl) {
        return of(row, verifyUrl, null);
    }

    public static CertificateResponse of(CertificateRow row, String verifyUrl, String downloadUrl) {
        return new CertificateResponse(row.getCertificateId(), row.getSerialNo(), row.getCertType(),
                row.getStatus(), row.getCourseName(), row.getIssuedAt(), row.getRevokedAt(),
                verifyUrl, downloadUrl);
    }
}
