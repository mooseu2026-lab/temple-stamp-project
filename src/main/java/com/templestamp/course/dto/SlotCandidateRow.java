// src/main/java/com/templestamp/course/dto/SlotCandidateRow.java
package com.templestamp.course.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** slot_site JOIN site(+i18n). SlotSiteMapper.findByCourse 의 resultType */
@Getter @Setter @NoArgsConstructor
public class SlotCandidateRow {
    private Long courseSiteId;
    private Long siteId;
    private String siteName;
    private String track;
    private Integer sortNo;
    private String routeNote;
    private Boolean isStar;
    private String servingNote;
    private Boolean congested;   // Q3 과포화 — 목록에 남기되 화면에서 "혼잡" 배지
    private String siteStatus;
}
