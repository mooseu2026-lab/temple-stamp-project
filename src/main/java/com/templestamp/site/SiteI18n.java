package com.templestamp.site;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteI18n {

    private Long siteI18nId;
    private Long siteId;
    private String locale;
    private String name;
    private String description;
    private String parkingInfo;
    private String accessInfo;
}
