package com.templestamp.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PENDING 스탬프를 승인·반려할 때 쓰는 DTO Record입니다.
 * APPROVE → COMPLETED 확정 + 완주 재집계(CompletionService) / REJECT → REJECTED 종단.
 * REJECT 일 때 reason 필수 — 이 조건부 검증은 Service 가 합니다.
 * [사용 위치] AdminStampController — @RequestBody @Valid
 */
public record StampReviewRequest(

        @NotBlank(message = "처리 결과를 입력해주세요.")
        @Pattern(regexp = "^(APPROVE|REJECT)$", message = "APPROVE 또는 REJECT 만 가능합니다.")
        String decision,

        @Size(max = 300, message = "사유는 300자 이하로 입력해주세요.")
        String reason              // REJECT 시 필수(사용자 안내에 쓰임) — Service 검증
) {
    public boolean isApprove() { return "APPROVE".equals(decision); }
}
