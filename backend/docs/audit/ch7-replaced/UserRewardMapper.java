package com.templestamp.reward;

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

    Optional<UserReward> findById(@Param("userRewardId") Long userRewardId);

    /** 방금 적립한 행을 근거 키로 되찾는다. 목록 응답에 바로 실어 주기 위해서다. */
    Optional<RewardRow> findRow(@Param("rewardPolicyId") Long rewardPolicyId,
                                @Param("stampId") Long stampId,
                                @Param("pilgrimageId") Long pilgrimageId);

    List<RewardRow> findRowsByUserId(@Param("userId") Long userId);

    /** 청구 처리. GRANTED 상태일 때만 바뀐다 — 중복 청구를 SQL 이 막는다. */
    int claim(@Param("userRewardId") Long userRewardId,
              @Param("status") String status,
              @Param("auditScore") BigDecimal auditScore,
              @Param("auditDetail") String auditDetail);

    int resolve(@Param("userRewardId") Long userRewardId,
                @Param("status") String status,
                @Param("resolvedBy") Long resolvedBy);

    List<RewardRow> findUnderReview();
}
