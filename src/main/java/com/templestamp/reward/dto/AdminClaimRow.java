package com.templestamp.reward.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 관리자 심사 목록 한 줄. <b>배송 정보가 실리는 유일한 응답</b>이다(챕터 7 보강 B-4).
 * 사용자용 RewardRow 와 따로 두는 이유가 그것이다 — 같은 DTO 를 쓰면 언젠가
 * 사용자 목록 질의가 주소를 함께 조인하게 된다.
 * [사용 위치] UserRewardMapper.findUnderReview → AdminRewardController
 */
@Getter
@Setter
@NoArgsConstructor
public class AdminClaimRow {
    private Long userRewardId;
    private Long userId;
    private String nickname;
    private String rewardName;
    private String rewardType;
    private String status;
    private boolean needsReview;      // 완주가 취소됐는데 이미 움직인 보상 — 맨 위로 온다
    private String courseName;
    private LocalDateTime earnedAt;
    private LocalDateTime claimedAt;
    private String trackingNo;        // 승인(PAID) 때 함께 받은 송장. 그 전에는 null

    /* ---- 배송 정보. 신청 전이면 전부 null ---- */
    private String recipientName;
    private String phone;
    private String address;
    private String memo;
}
