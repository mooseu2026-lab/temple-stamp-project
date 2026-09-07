package com.templestamp.course;

import com.templestamp.course.dto.RegionRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RegionMapper {

    List<RegionRow> findAll();

    /** v4 시더 — code → region_id 매핑용. RegionRow 가 아니라 도메인으로 받는다. */
    java.util.List<Region> findAllRegions();

    int existsById(@Param("regionId") Long regionId);
}
