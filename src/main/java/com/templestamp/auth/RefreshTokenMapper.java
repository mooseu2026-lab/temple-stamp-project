package com.templestamp.auth;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface RefreshTokenMapper {

    int save(RefreshToken refreshToken);

    Optional<RefreshToken> findByTokenHash(@Param("tokenHash") String tokenHash);

    int revoke(@Param("tokenHash") String tokenHash);

    /** 로그아웃 / 토큰 재사용 감지 시 그 사용자의 세션을 전부 끊는다. */
    int revokeAllByUser(@Param("userId") Long userId);

    /** 만료된 행 정리. 스케줄러가 하루 한 번 호출한다. */
    /**
     * 만료 시각이 {@code cutoff} 보다 이른 행을 한 번에 {@code limit} 건까지 지운다(챕터 9 §5 ②).
     * 폐기 여부는 보지 않는다 — 만료가 기준이다. 폐기됐어도 미만료면 남는다.
     */
    int deleteExpiredBefore(@Param("cutoff") java.time.LocalDateTime cutoff, @Param("limit") int limit);
}
