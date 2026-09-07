package com.templestamp.reward;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * user_reward 테이블 도메인 클래스 — 내 보상 한 건.
 * <pre>
 *   GRANTED  적립됨 (트리거 발생 시 자동)
 *      ↓ 사용자가 청구
 *   PAID          감사 점수가 낮으면 즉시 지급
 *   UNDER_REVIEW  점수가 높으면 관리자 심사 → PAID | REJECTED
 * </pre>
 * 중복 적립은 DB가 막는다. (정책, 도장) 과 (정책, 순례) 각각에 UNIQUE 가 걸려 있다.
 */
@Getter
@Setter
@NoArgsConstructor
public class UserReward {

    public static final String GRANTED = "GRANTED";
    public static final String CLAIMED = "CLAIMED";
    public static final String UNDER_REVIEW = "UNDER_REVIEW";
    public static final String PAID = "PAID";
    public static final String REJECTED = "REJECTED";

    private Long userRewardId;
    private Long userId;
    private Long rewardPolicyId;
    private Long stampId;
    private Long pilgrimageId;
    private String status;
    private BigDecimal auditScore;
    private String auditDetail;
    private LocalDateTime grantedAt;
    private LocalDateTime claimedAt;
    private LocalDateTime resolvedAt;
    private Long resolvedBy;
}
