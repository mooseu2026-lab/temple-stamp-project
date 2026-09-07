package com.templestamp.stamp.dto;

import java.time.LocalDateTime;

/**
 * 예외 경로 접수가 완료되었음을 알리는 DTO Record입니다.
 * 관리자 승인 전이므로 보상 정보를 담지 않습니다.
 * [사용 위치] StampService 조립 → Controller 반환
 */
public record EvidenceResponse(
        Long stampId,
        String status,               // PENDING
        String message,              // 안내 문구
        LocalDateTime requestedAt
) {
}
