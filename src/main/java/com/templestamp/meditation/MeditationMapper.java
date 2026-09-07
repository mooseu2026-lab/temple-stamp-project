package com.templestamp.meditation;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface MeditationMapper {

    /** category 가 null 이면 전체. 값은 category_no 를 문자열로 받은 것이다. */
    List<Meditation> findAllActive(@Param("category") String category);

    Optional<Meditation> findById(@Param("meditationId") Long meditationId);

    List<CategoryCountRow> countByCategory();
}
