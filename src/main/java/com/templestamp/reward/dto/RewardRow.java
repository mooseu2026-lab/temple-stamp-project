package com.templestamp.reward.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 사용자가 받은 보상에 보상 정책의 이름·유형을 조인해 담는 조회 전용 DTO 클래스입니다.
 * status 는 DB CHECK 값(GRANTED / CLAIMED / UNDER_REVIEW / PAID / REJECTED)을 그대로 담는다.
 * [사용 위치] UserRewardMapper 의 resultType (user_reward JOIN reward_policy)
 */
@Getter
@Setter
@NoArgsConstructor
public class RewardRow {
    private Long userRewardId;
    private Long userId;
    private String nickname;
    private Long rewardPolicyId;
    private String rewardName;     // reward_policy.name 조인
    private String rewardType;     // STAMP / COUPON / PHYSICAL
    private String status;
    private boolean needsReview;   // 완주가 취소됐는데 이미 움직인 보상 — 심사 목록 맨 위로 온다
    private Long stampId;
    private Long pilgrimageId;
    private String courseName;
    private LocalDateTime earnedAt;    // user_reward.granted_at
    private LocalDateTime claimedAt;   // 미청구면 null
}
