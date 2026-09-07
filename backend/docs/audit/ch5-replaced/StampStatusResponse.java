package com.templestamp.stamp.dto;

import java.time.LocalDateTime;

/**
 * 스탬프 한 건의 현재 상태와 각 단계 통과 시각을 내려주는 DTO Record입니다.
 * [사용 위치] StampService 조립 → Controller 반환. 상태는 String(StampStatus.name())
 */
public record StampStatusResponse(
        Long stampId,
        Long courseSiteId,
        String status,                    // GPS_DONE / QR_DONE / COMPLETED / EXPIRED / PENDING / REJECTED
        LocalDateTime gpsVerifiedAt,      // 1단계 통과 시각
        LocalDateTime qrVerifiedAt,       // 2단계 통과 시각 — 미통과면 null
        LocalDateTime completedAt,        // 3단계 확정 시각 — 미확정이면 null
        LocalDateTime expiresAt           // gps_verified_at + 60분 (계산값)
) {
}
