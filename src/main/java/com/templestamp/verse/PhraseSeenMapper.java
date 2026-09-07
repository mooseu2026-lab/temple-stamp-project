package com.templestamp.verse;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PhraseSeenMapper {

    /** 연타로 인한 중복은 오류가 아니라 정상이다. INSERT IGNORE 로 넣는다. */
    int markSeen(@Param("userId") Long userId,
                 @Param("courseId") Long courseId,
                 @Param("expansionPhraseId") Long expansionPhraseId);

    int countByUserId(@Param("userId") Long userId);
}
