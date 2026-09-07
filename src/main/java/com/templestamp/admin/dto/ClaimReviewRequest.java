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

        @Size(max = 200, message = "사유는 200자 이하로 입력해주세요.")
        String reason,

        /**
         * 송장 문자열(선택). 발송을 별도 상태로 두지 않고 승인(PAID) 전이 때 함께 받는다 —
         * 상태를 하나 더 만들면 그 상태에서만 되는 일·안 되는 일을 또 정해야 하는데,
         * 지금 발송에 필요한 것은 "언제 무엇으로 보냈는가" 한 줄뿐이다(챕터 7 보강 A §4-2).
         * 반려에는 쓰이지 않는다.
         */
        @Size(max = 100, message = "송장 번호는 100자 이하로 입력해주세요.")
        String trackingNo
) {
    public boolean isApprove() { return "APPROVE".equals(decision); }
}
