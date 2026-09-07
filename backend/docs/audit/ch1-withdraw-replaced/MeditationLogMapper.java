package com.templestamp.meditation;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MeditationLogMapper {

    int save(MeditationLog log);

    List<MeditationLog> findByUserId(@Param("userId") Long userId,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    int sumPlayedSec(@Param("userId") Long userId);
}
