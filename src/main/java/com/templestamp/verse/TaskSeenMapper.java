package com.templestamp.verse;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskSeenMapper {

    int markSeen(@Param("userId") Long userId,
                 @Param("courseId") Long courseId,
                 @Param("missionId") Long missionId);

    int countByUserId(@Param("userId") Long userId);
}
