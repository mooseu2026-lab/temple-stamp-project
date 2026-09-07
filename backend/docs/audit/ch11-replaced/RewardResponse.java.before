package com.templestamp.reward.dto;

import java.time.LocalDateTime;

/**
 * 보상 목록 한 건을 내려주는 DTO Record입니다.
 * claimable 은 실물 보상이면서 아직 청구 전인지를 계산한 값이고,
 * 감사 점수와 상세는 절대 담지 않습니다(어뷰징 우회 방지).
 * [사용 위치] RewardService 가 RewardRow → 변환. MissionResultResponse.rewards 에도 재사용
 */
public record RewardResponse(
        Long userRewardId,
        String rewardName,
        String rewardType,
        String status,
        boolean claimable,          // PHYSICAL && 청구 전 — 계산값
        LocalDateTime earnedAt
) {
    /**
     * claimable 은 여기서 계산하지 않는다 — RewardService.claimable() 한 곳에서 계산해 넘겨받는다.
     * 예전에는 이 자리에서 "PHYSICAL 이고 청구 시각이 없으면 true" 로 따로 셈했고,
     * 그래서 신청 API 의 판정과 어긋나 claimable:false 인 보상도 신청이 통과했다(결함 5-1).
     */
    public static RewardResponse from(RewardRow row, boolean claimable) {
        return new RewardResponse(row.getUserRewardId(), row.getRewardName(), row.getRewardType(),
                row.getStatus(), claimable, row.getEarnedAt());
    }
}
