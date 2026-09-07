package com.templestamp.user;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface UserMapper {

    /** EXISTS 결과를 0/1 로 받는다. */
    int existsByEmail(@Param("email") String email);

    /** 저장 후 user.userId 에 생성된 PK 가 채워진다. */
    int save(User user);

    Optional<User> findByEmail(@Param("email") String email);

    Optional<User> findById(@Param("userId") Long userId);

    /**
     * 완주 연쇄가 시작되기 전에 그 사용자를 잠근다(챕터 7 보강 B-1).
     * 한 사람의 도장 두 개가 동시에 5칸째를 채우는 경우가 실제로 있고, 그때
     * 완주 행·인증서·보상을 각자 만들려다 서로를 기다린다. 잠금 순서의 첫 칸이다.
     */
    int lockById(@Param("userId") Long userId);

    /** 싱글페이지 등 tier 만 필요한 요청에서 12컬럼 전체 조회를 피한다. */
    String findTier(@Param("userId") Long userId);

    /**
     * 언어 우선순위(쿼리 → Accept-Language → users.locale → ko)의 세 번째 자리.
     * 앞의 둘에서 정해지면 이 조회 자체가 일어나지 않는다.
     */
    String findLocale(@Param("userId") Long userId);

    /** 읽고-판단하고-쓰는 두 단계로 나누지 않는다. 동시 요청에도 횟수가 정확하다. */
    int recordLoginFail(@Param("userId") Long userId,
                        @Param("maxFail") int maxFail,
                        @Param("lockMinutes") int lockMinutes);

    int resetLoginFail(@Param("userId") Long userId);

    int updateProfile(@Param("userId") Long userId,
                      @Param("nickname") String nickname,
                      @Param("locale") String locale,
                      @Param("notificationEnabled") Boolean notificationEnabled);

    int updateTier(@Param("userId") Long userId, @Param("tier") String tier);

    int updatePassword(@Param("userId") Long userId, @Param("password") String password);
}
