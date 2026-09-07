package com.templestamp.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * stamp.* — 인증 진행 규칙.
 */
@ConfigurationProperties(prefix = "stamp")
public record StampProperties(
        /** GPS 통과 후 QR·다짐까지 마쳐야 하는 시간(분). 확정 60. */
        int sessionMinutes,
        /** site_distance 에 없는 사찰 쌍의 기본 최소 이동시간(분). 0이면 검사하지 않는다. */
        int defaultTravelMinutes,
        /** 하루에 받을 수 있는 도장 수. 명세 확정 5. */
        int dailyLimit,
        /** 예외 접수 하루 한도. 명세 확정 2. */
        int evidenceDailyLimit
) {
}
