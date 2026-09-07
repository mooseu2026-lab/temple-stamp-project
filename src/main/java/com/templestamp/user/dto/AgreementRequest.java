package com.templestamp.user.dto;

import com.templestamp.global.type.AgreementType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 약관 동의 내역을 전달받는 DTO Record입니다.
 * 어떤 약관의 몇 번째 버전에 동의했는지를 받습니다.
 * [사용 위치] UserController — @RequestBody @Valid
 */
public record AgreementRequest(

        @NotNull(message = "약관 종류를 선택해주세요.")
        AgreementType agreementType,

        @NotNull(message = "약관 버전이 필요합니다.")
        @Positive(message = "약관 버전이 올바르지 않습니다.")
        Integer version
) {
}
