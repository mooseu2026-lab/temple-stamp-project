package com.templestamp.reward.dto;

/**
 * 실물 보상 청구 결과를 내려주는 DTO Record입니다.
 * 사후 감사 점수에 따라 즉시 지급(PAID)인지 검토 대기(UNDER_REVIEW)인지를 상태로 알려줍니다.
 * 감사 점수 자체는 응답에 담지 않습니다 — RewardResponse 와 같은 원칙.
 * [사용 위치] RewardService 조립 → Controller 반환
 */
public record ClaimResponse(
        Long userRewardId,
        String status,          // CLAIMED(신청 접수) 또는 UNDER_REVIEW(검토 대기)
        boolean claimable,      // 접수된 뒤에는 언제나 false — 프론트가 버튼을 바로 끌 수 있게 함께 내려준다
        String message          // 사용자 안내 문구
) {
}
