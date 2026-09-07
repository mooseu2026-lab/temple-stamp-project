// src/main/java/com/templestamp/course/SlotSite.java
package com.templestamp.course;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** slot_site 1행 — 한 슬롯(course_site)의 후보 사찰 하나. track 은 MAIN/SUNROAD 문자열 */
@Getter @Setter @NoArgsConstructor
public class SlotSite {
    private Long slotSiteId;
    private Long courseSiteId;
    private Long siteId;
    private String track;
    private Integer sortNo;
    private String routeNote;
    private Boolean isStar;
    private String servingNote;
}
