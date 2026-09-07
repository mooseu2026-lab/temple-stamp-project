package com.templestamp.certificate.dto;

import java.time.LocalDateTime;

/**
 * 인증서 QR 을 스캔한 제3자에게 진위 확인 결과를 내려주는 DTO Record입니다.
 * 비로그인 상태로 나가는 유일한 개인 데이터이므로 닉네임을 마스킹하고,
 * userId·이메일·문장·사진은 담지 않습니다.
 * [사용 위치] CertificateService → 공개 Controller. 없는 일련번호는 이 DTO 를 만들지 않고 404 CERT-4041
 */
public record CertificateVerifyResponse(boolean valid, String serialNo, String certType,
                                        String courseName, String holderNickname, LocalDateTime issuedAt) {

    public static CertificateVerifyResponse from(CertificateRow row) {
        return new CertificateVerifyResponse(
                true,                       // 이 객체가 만들어졌다는 것 자체가 유효하다는 뜻
                row.getSerialNo(),
                row.getCertType(),
                row.getCourseName(),
                maskNickname(row.getNickname()),
                row.getIssuedAt());
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
