package com.templestamp.site.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * shrine_element(7행 전부) LEFT JOIN site_element(해당 사찰). SiteGuideMapper 의 resultType.
 * <p>
 * 항상 7행이 온다. siteElementId 가 null 이면 "이 절에는 이 요소가 없다" 는 뜻이며,
 * 그때도 행 자체는 사라지지 않는다 — 자리 수와 순서가 어느 절이든 같아야 하기 때문이다.
 */
@Getter @Setter @NoArgsConstructor @ToString
public class SiteGuideRow {
    private Long elementId;
    private String code;
    private String name;
    private Integer sortNo;
    private String meaning;
    private String etiquette;
    private String passageMeaning;
    private Long siteElementId;      // null → 이 절에는 없음
    private String localName;
    private String note;

    public boolean isPresent() {
        return siteElementId != null;
    }
}
