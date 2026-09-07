package com.templestamp.reward;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 실물 보상 배송 정보. 보상 하나에 한 행이고, 다시 신청하면 덮어쓴다.
 * 삭제 후 재삽입이 아니라 UPSERT 인 이유는 그 사이 아주 짧게 "신청은 있는데 주소가 없는" 상태가
 * 생기기 때문이다(관리자 목록 원칙 — 정리.md §3).
 */
@Mapper
public interface RewardClaimMapper {

    /** 탈퇴 — 배송 정보(이름·연락처·주소)는 남길 이유가 없다. 행째로 지운다. */
    int deleteByUser(@Param("userId") Long userId);

    int upsert(@Param("userRewardId") Long userRewardId,
               @Param("recipientName") String recipientName,
               @Param("phone") String phone,
               @Param("address") String address,
               @Param("memo") String memo);
}
