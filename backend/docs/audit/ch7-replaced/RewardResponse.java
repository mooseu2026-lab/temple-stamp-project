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
    public static RewardResponse from(RewardRow row) {
        boolean claimable = "PHYSICAL".equals(row.getRewardType()) && row.getClaimedAt() == null;
        return new RewardResponse(row.getUserRewardId(), row.getRewardName(), row.getRewardType(),
                row.getStatus(), claimable, row.getEarnedAt());
    }
}
