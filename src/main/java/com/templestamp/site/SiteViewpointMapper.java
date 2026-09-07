package com.templestamp.site;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SiteViewpointMapper {

    List<SiteViewpoint> findBySiteId(@Param("siteId") Long siteId);

    /** uk_site_viewpoint(site_id, sort_no) 기준 UPSERT. 같은 sortNo 를 다시 보내면 덮어쓴다. */
    int upsert(@Param("siteId") Long siteId,
              @Param("sortNo") Integer sortNo,
              @Param("locationDesc") String locationDesc,
              @Param("bestTime") String bestTime,
              @Param("whatToSee") String whatToSee);
}
