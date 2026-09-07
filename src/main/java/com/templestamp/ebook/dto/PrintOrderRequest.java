package com.templestamp.ebook.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 회향본 인쇄 신청에 필요한 부수와 배송지를 전달받고 검증하는 DTO Record입니다.
 * 부수는 1~5로 제한하며(DB CHECK 와 동일), 회향본인지·내 것인지 같은 자격 검증은 Service 가 합니다.
 * 연락처는 휴대폰 번호만 허용. 신청 확인 안내는 회원 본인 이메일로 발송(요청에 이메일 필드 없음).
 * [사용 위치] PrintOrderController — @RequestBody @Valid
 */
public record PrintOrderRequest(

        @NotNull(message = "대상 전자책이 필요합니다.")
        @Positive(message = "전자책 번호가 올바르지 않습니다.")
        Long ebookId,

        @NotNull(message = "부수를 입력해주세요.")
        @Min(value = 1, message = "부수는 1부 이상이어야 합니다.")
        @Max(value = 5, message = "부수는 5부까지 신청할 수 있습니다.")
        Integer quantity,

        @NotBlank(message = "수령인 이름을 입력해주세요.")
        @Size(max = 50, message = "수령인 이름은 50자 이하로 입력해주세요.")
        String recipientName,

        @NotBlank(message = "수령인 연락처를 입력해주세요.")
        @Pattern(
                regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$",
                message = "휴대폰 번호 형식이 아닙니다. (예: 010-1234-5678)"
        )
        String recipientPhone,

        @NotBlank(message = "우편번호를 입력해주세요.")
        @Pattern(regexp = "^\\d{5}$", message = "우편번호는 숫자 5자리입니다.")
        String postalCode,

        @NotBlank(message = "주소를 입력해주세요.")
        @Size(max = 255, message = "주소는 255자 이하로 입력해주세요.")
        String address,

        @Size(max = 255, message = "상세 주소는 255자 이하로 입력해주세요.")
        String addressDetail,          // 선택

        @Size(max = 500, message = "메모는 500자 이하로 입력해주세요.")
        String note                    // 선택
) {
}
