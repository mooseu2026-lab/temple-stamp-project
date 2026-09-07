package com.templestamp.verse;

import com.templestamp.global.type.Tier;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface MissionMapper {

    /** 이 코스에서 아직 안 받은 미션 하나. 검수를 마친 것을 먼저 준다. */
    /**
     * 세 풀 중 <b>어느 풀에 아직 안 본 과제가 남아 있는지</b>를 한 번에 센다.
     * 풀마다 따로 물어보면 왕복이 셋이 되고, 그 사이에 다른 요청이 끼어들면 센 것과 고르는 것이 달라진다.
     */
    List<ScopeCount> countUnseenByScope(@Param("userId") Long userId,
                                        @Param("courseId") Long courseId,
                                        @Param("siteId") Long siteId,
                                        @Param("verseNo") Integer verseNo,
                                        @Param("tier") Tier tier);

    /** 고른 풀 안에서 아직 안 본 것 하나. 검수된 것을 먼저 준다. */
    Optional<Mission> findUnseenInScope(@Param("userId") Long userId,
                                        @Param("courseId") Long courseId,
                                        @Param("siteId") Long siteId,
                                        @Param("verseNo") Integer verseNo,
                                        @Param("tier") Tier tier,
                                        @Param("scope") MissionScope scope);

    /** 세 풀이 다 비었을 때의 대체안. 미션 없이 도장을 끝낼 수 없으므로 하나는 반드시 준다(챕터 5). */
    Optional<Mission> findAnyForFallback(@Param("siteId") Long siteId,
                                         @Param("verseNo") Integer verseNo,
                                         @Param("tier") Tier tier);

    /** 풀에 남은 것이 몇 편인지. {@code scope} 별 집계 결과를 담는 그릇이다. */
    record ScopeCount(MissionScope scope, int cnt) {
    }

    /** 변형을 다 받아 본 경우의 대체안. */
    Optional<Mission> findById(@Param("missionId") Long missionId);

    int upsert(Mission mission);

    /** 그 (구절·대상) 조합의 다음 변형 번호. 없으면 1. */
    int nextVariantNo(@Param("verseNo") Integer verseNo,
                      @Param("tier") Tier tier,
                      @Param("scope") MissionScope scope,
                      @Param("siteId") Long siteId);
}
