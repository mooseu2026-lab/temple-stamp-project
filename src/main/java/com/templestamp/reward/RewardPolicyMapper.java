package com.templestamp.reward;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface RewardPolicyMapper {

    Optional<RewardPolicy> findById(@Param("rewardPolicyId") Long rewardPolicyId);

    /** 이 트리거로 적립할 활성 정책들. */
    List<RewardPolicy> findByTrigger(@Param("triggerType") String triggerType);

    List<RewardPolicy> findAllActive();
}
