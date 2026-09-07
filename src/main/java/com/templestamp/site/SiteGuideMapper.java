package com.templestamp.site;

import com.templestamp.site.dto.SiteGuideRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SiteGuideMapper {

    /**
     * 사전 7행 전부에 이 사찰의 보유 여부를 붙여 온다. 결과는 언제나 7건, sort_no 순이다.
     * 사찰이 아무 요소도 갖고 있지 않아도 7건이 나온다.
     */
    List<SiteGuideRow> findGuideRows(@Param("siteId") Long siteId);
}
