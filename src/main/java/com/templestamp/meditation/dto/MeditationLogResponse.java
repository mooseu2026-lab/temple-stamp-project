package com.templestamp.meditation.dto;

import java.time.LocalDateTime;

/**
 * 저장된 명상 재생 기록을 내려주는 DTO Record입니다.
 * [사용 위치] MeditationService 조립 → Controller 반환
 */
public record MeditationLogResponse(
        Long meditationLogId,
        Long meditationId,
        String title,
        Integer playedSeconds,
        String memo,
        LocalDateTime createdAt
) {
}
