package com.templestamp.certificate.dto;

import java.time.LocalDateTime;

/**
 * 인증서 QR 을 스캔한 제3자에게 진위 확인 결과를 내려주는 DTO Record입니다.
 * 비로그인 상태로 나가는 유일한 개인 데이터이므로 닉네임을 마스킹하고,
 * userId·이메일·전화·주소·문장·사진은 담지 않습니다(챕터 7 §3-4 — 필드는 아래 일곱뿐).
 * [사용 위치] CertificateService → 공개 Controller. 없는 일련번호는 이 DTO 를 만들지 않고 404 CERT-4041
 */
public record CertificateVerifyResponse(String serialNo, String certType, String status,
                                        String courseName, String holderMasked,
                                        LocalDateTime issuedAt, LocalDateTime revokedAt) {

    public static CertificateVerifyResponse from(CertificateRow row) {
        return new CertificateVerifyResponse(
                row.getSerialNo(),
                row.getCertType(),
                row.getStatus(),            // 회수된 번호는 여기서 REVOKED 로 드러난다
                row.getCourseName(),
                maskNickname(row.getNickname()),
                row.getIssuedAt(),
                row.getRevokedAt());
    }

    /**
     * 닉네임 마스킹. 별표는 닉네임 길이와 무관하게 항상 1개다(길이 노출 방지).
     * 3자 이상 : 첫 글자 + * + 마지막 글자   순례자 → 순*자
     * 2자      : 첫 글자 + *                민수 → 민*
     * 1자·빈값  : *
     */
    public static String maskNickname(String nickname) {
        if (nickname == null || nickname.isEmpty()) {
            return "*";
        }

        int length = nickname.length();

        if (length == 1) {
            return "*";
        }
        if (length == 2) {
            return nickname.substring(0, 1) + "*";
        }
        return nickname.substring(0, 1) + "*" + nickname.substring(length - 1);
    }
}
