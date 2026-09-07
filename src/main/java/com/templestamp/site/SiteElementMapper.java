// src/main/java/com/templestamp/site/SiteElementMapper.java
package com.templestamp.site;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface SiteElementMapper {
    /** shrine_element 사전 7행: elementId, code, sortNo, name */
    List<Map<String, Object>> findDictionary();

    int upsert(@Param("siteId") Long siteId, @Param("elementId") Long elementId,
               @Param("localName") String localName, @Param("note") String note);

    int delete(@Param("siteId") Long siteId, @Param("elementId") Long elementId);
}
