package com.templestamp.reward;

import com.templestamp.reward.dto.AdminClaimRow;
import com.templestamp.reward.dto.RewardRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Mapper
public interface UserRewardMapper {

    /**
     * 적립. 같은 근거로 이미 적립돼 있으면 조용히 넘어간다(INSERT IGNORE).
     * 완주 처리가 재시도돼도 보상이 두 번 쌓이지 않는다.
     *
     * @return 실제로 삽입된 행 수 (0 이면 이미 있었다는 뜻)
     */
    int grant(UserReward userReward);

    /** 수령 신청 검사용. 소유자·종류·상태를 한 번에 봐야 하므로 정책을 조인한 행으로 읽는다. */
    Optional<RewardRow> findRowById(@Param("userRewardId") Long userRewardId);

    /** 방금 적립한 행을 근거 키로 되찾는다. 목록 응답에 바로 실어 주기 위해서다. */
    Optional<RewardRow> findRow(@Param("rewardPolicyId") Long rewardPolicyId,
                                @Param("stampId") Long stampId,
                                @Param("pilgrimageId") Long pilgrimageId);

    List<RewardRow> findRowsByUserId(@Param("userId") Long userId);

    /** 수령 신청 접수. GRANTED·REJECTED 일 때만 바뀐다 — 중복 신청을 SQL 이 막는다. */
    int claim(@Param("userRewardId") Long userRewardId,
              @Param("status") String status,
              @Param("auditScore") BigDecimal auditScore,
              @Param("auditDetail") String auditDetail);

    /**
     * 완주가 다시 성립했을 때, 회수됐던 그 보상을 되살린다(챕터 7 보강 A §2-4).
     * 새로 적립하지 않는 이유는 멱등 키가 두 번째 적립을 막기 때문이다 —
     * 그대로 두면 인증서는 다시 나오는데 보상만 영영 REVOKED 로 남는다.
     * 신청·심사 흔적은 지운다. 되살아난 보상은 "아직 아무도 손대지 않은" 상태여야 한다.
     */
    int restoreRevoked(@Param("rewardPolicyId") Long rewardPolicyId,
                       @Param("stampId") Long stampId,
                       @Param("pilgrimageId") Long pilgrimageId);

    /** 심사 결과 반영. 신청 접수(CLAIMED)·검토 대기(UNDER_REVIEW) 둘 다에서 넘어온다. */
    int resolve(@Param("userRewardId") Long userRewardId,
                @Param("status") String status,
                @Param("resolvedBy") Long resolvedBy,
                @Param("trackingNo") String trackingNo);

    /**
     * 심사할 것들. 사람이 봐야 하는 건(needs_review)이 맨 위로 온다.
     * <b>이 질의만</b> 배송 정보를 조인한다 — 사용자 목록(findRowsByUserId)에는 주소가 실리지 않는다.
     */
    List<AdminClaimRow> findUnderReview();

    /* ---------------- 완주 취소 연쇄 (챕터 7 §2-4) ---------------- */

    /** 아직 아무도 움직이지 않은 적립분만 회수한다. */
    int revokeGrantedByPilgrimage(@Param("pilgrimageId") Long pilgrimageId);

    /**
     * 신청·심사·지급까지 간 것은 상태를 바꾸지 않고 표시만 켠다.
     * 실물이 이미 배송됐을 수 있어 시스템이 스스로 되돌릴 수 없다 — 사람이 본다.
     */
    int flagNeedsReviewByPilgrimage(@Param("pilgrimageId") Long pilgrimageId);

    /** 회향 보상은 특정 완주가 아니라 "사용자 + 트리거" 로 찾는다. */
    int revokeGrantedByTrigger(@Param("userId") Long userId,
                               @Param("triggerType") String triggerType);

    int flagNeedsReviewByTrigger(@Param("userId") Long userId,
                                 @Param("triggerType") String triggerType);
}
