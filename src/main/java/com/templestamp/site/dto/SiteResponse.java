package com.templestamp.site.dto;

import java.math.BigDecimal;

/**
 * 사찰 기본 정보를 내려주는 DTO Record입니다.
 * 좌표·인증 반경·QR 위치 안내 등 인증에 필요한 값을 포함합니다.
 * [사용 위치] SiteService 조립 → Controller 반환
 */
public record SiteResponse(
        Long siteId,
        String name,               // site_i18n.name (locale), 없으면 site.name
        String description,        // site_i18n.description — 해당 locale 행 없으면 null
        BigDecimal latitude,
        BigDecimal longitude,
        Integer verifyRadius,
        String qrLocationHint
) {
}
