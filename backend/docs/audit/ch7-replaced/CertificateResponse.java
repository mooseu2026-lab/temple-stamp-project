package com.templestamp.certificate.dto;

import java.time.LocalDateTime;

/**
 * 내가 받은 인증서 한 건을 내려주는 DTO Record입니다.
 * 제3자가 진위를 확인할 수 있는 URL 을 함께 담습니다.
 * [사용 위치] CertificateService 가 CertificateRow + 검증 URL 조립 → Controller 반환
 */
public record CertificateResponse(
        Long certificateId,
        String serialNo,
        String certType,
        String courseName,
        LocalDateTime issuedAt,
        String verifyUrl            // {base}/api/certificates/verify/{serialNo}
) {
    public static CertificateResponse of(CertificateRow row, String verifyUrl) {
        return new CertificateResponse(row.getCertificateId(), row.getSerialNo(), row.getCertType(),
                row.getCourseName(), row.getIssuedAt(), verifyUrl);
    }
}
