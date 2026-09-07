package com.templestamp.user.dto;

import java.time.LocalDateTime;

/**
 * 저장된 약관 동의 내역을 내려주는 DTO Record입니다.
 * 같은 버전에 다시 동의해도 조용히 무시되고 최초 동의 시각이 유지됩니다.
 * [사용 위치] AgreementService 조립 → Controller 반환. enum 은 응답에서 String
 */
public record AgreementResponse(
        String agreementType,
        Integer version,
        LocalDateTime agreedAt
) {
}
