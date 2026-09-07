package com.templestamp.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 감사 점수로 UNDER_REVIEW 상태가 된 실물 보상 청구를 승인·거부하는 요청 DTO Record입니다.
 * 감사 점수·판단 근거는 요청에도 응답에도 싣지 않습니다(어뷰징 우회 방지 — RewardResponse 와 같은 원칙).
 * [사용 위치] AdminRewardController — @RequestBody @Valid
 */
public record ClaimReviewRequest(

        @NotBlank(message = "처리 결과를 입력해주세요.")
        @Pattern(regexp = "^(APPROVE|REJECT)$", message = "APPROVE 또는 REJECT 만 가능합니다.")
        String decision,

        @Size(max = 300, message = "사유는 300자 이하로 입력해주세요.")
        String reason
) {
    public boolean isApprove() { return "APPROVE".equals(decision); }
}
