// src/main/java/com/templestamp/admin/dto/AdminSiteListResponse.java
package com.templestamp.admin.dto;

import com.templestamp.site.Site;

import java.math.BigDecimal;

/**
 * 관리자 사찰 목록의 한 줄. 공개 응답({@code SiteResponse})과 나눈 이유는 <b>{@code status}</b> 하나 때문이다.
 * <p>
 * 관리자는 목록에서 "이게 공개된 것인가" 를 봐야 하는데 공개 응답에는 그 필드가 없다(공개된 것만 나가므로 필요가 없다).
 * 공개 DTO 에 넣으면 사용자 화면에도 따라 나가므로 여기에 따로 둔다.
 */
public record AdminSiteListResponse(
        Long siteId,
        String name,
        String status,              // DRAFT / ACTIVE / INACTIVE
        BigDecimal latitude,
        BigDecimal longitude,
        Integer verifyRadius,
        String qrLocationHint       // 비어 있으면 ACTIVE 로 올릴 수 없다
) {
    public static AdminSiteListResponse from(Site site) {
        return new AdminSiteListResponse(site.getSiteId(), site.getName(), site.getStatus(),
                site.getLatitude(), site.getLongitude(), site.getVerifyRadius(), site.getQrLocationHint());
    }
}
