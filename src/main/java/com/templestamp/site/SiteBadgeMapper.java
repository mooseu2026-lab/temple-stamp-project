package com.templestamp.site;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SiteBadgeMapper {

    List<SiteBadge> findBySiteId(@Param("siteId") Long siteId);

    /** uk_site_badge(site_id, badge_type) 기준 UPSERT. */
    int upsert(@Param("siteId") Long siteId,
              @Param("badgeType") String badgeType,
              @Param("description") String description);
}
