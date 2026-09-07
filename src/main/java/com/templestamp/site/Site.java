package com.templestamp.site;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 사찰.
 * <p>
 * latitude/longitude 는 시설의 고정 위치이며 개인 위치정보가 아니다. 다만 응답으로는 내보내지
 * 않는다 — 좌표를 손에 쥐면 현장에 가지 않고도 "가까움" 을 만들어 낼 수 있기 때문이다.
 * 거리 계산은 앱이 지도 SDK 로 받은 좌표로 하고, 서버에는 판정 결과만 온다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Site {

    public static final String DRAFT = "DRAFT";
    public static final String ACTIVE = "ACTIVE";
    public static final String INACTIVE = "INACTIVE";

    private Long siteId;
    private String name;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer verifyRadius;
    private Integer qrVersion;
    private String qrLocationHint;
    private String parkingInfo;
    private String accessInfo;
    private String mealAvailable;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isActive() {
        return ACTIVE.equals(status);
    }

    /** 가는 법 안내를 화면에 띄울 만큼 정보가 있는가. */
    public boolean hasAccessGuide() {
        return (parkingInfo != null && !parkingInfo.isBlank())
                || (accessInfo != null && !accessInfo.isBlank());
    }
}
