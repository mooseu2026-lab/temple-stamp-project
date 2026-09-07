package com.templestamp.verse;

import com.templestamp.global.type.Tier;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface ExpansionPhraseMapper {

    /**
     * 이 코스에서 아직 안 본 문구 하나. 검수를 마친(REVIEWED) 문구를 먼저 준다.
     */
    /**
     * 완화 1단계 — 안 읽은 문구 중 <b>이 코스에서 쓴 버전 번호와 겹치지 않는</b> 것.
     * 다섯 자리에서 다섯 버전을 각각 다르게 읽게 하려는 조건이다(챕터 10).
     */
    Optional<ExpansionPhrase> findUnseenWithNewVersion(
            @Param("userId") Long userId,
            @Param("courseId") Long courseId,
            @Param("verseNo") Integer verseNo,
            @Param("tier") Tier tier);

    /** 완화 2단계 — 안 읽은 것(버전 중복 허용). */
    Optional<ExpansionPhrase> findUnseen(@Param("userId") Long userId,
                                         @Param("courseId") Long courseId,
                                         @Param("verseNo") Integer verseNo,
                                         @Param("tier") Tier tier);

    /** 다섯 버전을 다 본 경우의 대체안. 가장 오래전에 본 것을 다시 준다. */
    Optional<ExpansionPhrase> findLeastRecentlySeen(@Param("userId") Long userId,
                                                    @Param("courseId") Long courseId,
                                                    @Param("verseNo") Integer verseNo,
                                                    @Param("tier") Tier tier);

    Optional<ExpansionPhrase> findById(@Param("expansionPhraseId") Long expansionPhraseId);

    int upsert(ExpansionPhrase phrase);

    /** 그 (구절·대상) 조합의 다음 빈 버전 번호. 없으면 1. */
    int nextVersionNo(@Param("verseNo") Integer verseNo, @Param("tier") Tier tier);

    /** 챕터 5 — 클라이언트가 보낸 확장문구가 이 구절·이 계층의 것인지. 아니면 기록하지 않는다. */
    boolean existsForVerseAndTier(@Param("expansionPhraseId") Long expansionPhraseId,
                                  @Param("verseNo") Integer verseNo,
                                  @Param("tier") String tier);

    int countAll();

}
