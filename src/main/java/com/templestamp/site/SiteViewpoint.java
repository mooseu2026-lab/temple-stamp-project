package com.templestamp.site;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 뷰포인트. 사찰당 1~3개. "어디서, 언제, 무엇을 보는가" 세 가지를 알려 준다. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteViewpoint {

    private Long siteViewpointId;
    private Long siteId;
    private Integer sortNo;
    private String locationDesc;
    private String bestTime;
    private String whatToSee;
}
