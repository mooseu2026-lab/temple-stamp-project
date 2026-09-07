package com.templestamp.site;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface SiteI18nMapper {

    Optional<SiteI18n> findBySiteIdAndLocale(@Param("siteId") Long siteId,
                                             @Param("locale") String locale);

    int upsert(SiteI18n siteI18n);

    /** ACTIVE 전환 조건 검사용. ko 행이 있어야 사찰 이름의 원문이 존재한다. */
    boolean exists(@Param("siteId") Long siteId, @Param("locale") String locale);
}
