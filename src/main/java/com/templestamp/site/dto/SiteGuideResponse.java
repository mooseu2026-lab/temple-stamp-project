package com.templestamp.site.dto;

import java.util.List;

/**
 * 「사찰 가는 법」 — 60곳 공통 7자리를 이 사찰의 내용으로 채운 응답.
 * <p>
 * steps 는 언제나 7건이고 position 1~7 순이다. 그 절에 없는 요소도 자리를 지킨다.
 * 없는 것을 빼 버리면 절마다 화면 구성이 달라지고, 사용자는 "이 절은 뭔가 부족하다" 로 읽는다.
 * 자리를 지킨 채 그 요소의 의미와 지나온 길의 의미로 채우면 "이 절은 이렇게 생겼다" 가 된다.
 * <p>
 * [사용 위치] SiteGuideService 조립 → SiteController 반환
 */
public record SiteGuideResponse(Long siteId, String siteName, List<Step> steps) {

    /**
     * present=true  — localName·etiquette·note 가 채워지고, passage 는 이 자리로 오는 길 한 문장이다.
     * present=false — localName·etiquette·note 는 null 이고, passage 는 이 자리로 오는 길과
     *                 다음 자리로 가는 길을 이어 붙인 두 문장이다. 자리가 비어 보이지 않게 한다.
     *                 마지막 자리(부속전각)가 없을 때는 이어 붙일 다음 길이 없어 한 문장만 나간다.
     */
    public record Step(Integer position,
                       String code,
                       String name,
                       String localName,
                       boolean present,
                       String meaning,
                       String passage,
                       String etiquette,
                       String note) {
    }
}
