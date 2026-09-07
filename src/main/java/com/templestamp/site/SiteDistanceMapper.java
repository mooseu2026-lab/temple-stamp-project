package com.templestamp.site;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface SiteDistanceMapper {

    /** A → B 최소 이동시간(분). 등록되지 않은 쌍이면 비어 있고, 그때는 검사하지 않는다. */
    Optional<Integer> findMinMinutes(@Param("siteAId") Long siteAId,
                                     @Param("siteBId") Long siteBId);

    int upsert(@Param("siteAId") Long siteAId,
               @Param("siteBId") Long siteBId,
               @Param("minMinutes") int minMinutes);
}
