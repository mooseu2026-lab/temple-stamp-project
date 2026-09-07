package com.templestamp.stamp.dto;

import java.time.LocalDateTime;

/**
 * 1단계 통과 결과와 QR 을 찾을 위치 힌트를 내려주는 DTO Record입니다.
 * expiresAt 은 DB 컬럼이 아니라 GPS 인증 시각 + 60분으로 계산한 값입니다.
 * [사용 위치] StampService 조립 → Controller 반환
 */
public record GpsCheckResponse(
        Long stampId,
        String status,              // GPS_DONE
        String qrLocationHint,      // 다음 단계 안내
        LocalDateTime expiresAt     // gps_verified_at + 60분 (계산값)
) {
}
