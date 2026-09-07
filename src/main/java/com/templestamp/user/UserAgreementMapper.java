package com.templestamp.user;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserAgreementMapper {

    /**
     * 동의 기록. 같은 (회원·항목·버전) 이 이미 있으면 철회 상태만 되돌리고
     * agreed_at 은 건드리지 않는다 — 최초 동의 시각이 근거이기 때문이다.
     */
    int agree(@Param("userId") Long userId,
              @Param("agreementType") String agreementType,
              @Param("agreementVersion") String agreementVersion);

    int withdraw(@Param("userId") Long userId,
                 @Param("agreementType") String agreementType,
                 @Param("agreementVersion") String agreementVersion);

    /**
     * 그 종류로 유효하게 동의해 둔 것을 전부 철회한다.
     * 철회 요청은 버전을 지정하지 않는다 — 사용자는 "위치기반서비스 동의를 거둔다" 고 말하지
     * "v1 동의만 거두고 v2 는 남긴다" 고 하지 않는다.
     */
    /** 탈퇴 — 동의 행은 법정 보존이라 지우지 않고 철회 시각만 남긴다(챕터 1 보강 §3-3). */
    int withdrawAll(@Param("userId") Long userId);

    int withdrawByType(@Param("userId") Long userId,
                       @Param("agreementType") String agreementType);

    List<UserAgreement> findByUserId(@Param("userId") Long userId);

    /** 지정 버전에 유효하게 동의한 상태인가. */
    int isAgreed(@Param("userId") Long userId,
                 @Param("agreementType") String agreementType,
                 @Param("agreementVersion") String agreementVersion);
}
