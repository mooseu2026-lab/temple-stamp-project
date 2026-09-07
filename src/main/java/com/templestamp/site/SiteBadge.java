package com.templestamp.site;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사찰 뱃지. FLOWER(꽃절) / GUARDIAN(십일수호). */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteBadge {

    public static final String FLOWER = "FLOWER";
    public static final String GUARDIAN = "GUARDIAN";

    private Long siteBadgeId;
    private Long siteId;
    private String badgeType;
    private String description;
}
