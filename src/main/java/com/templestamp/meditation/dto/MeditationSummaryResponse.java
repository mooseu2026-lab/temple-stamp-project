package com.templestamp.meditation.dto;

/**
 * 명상 목록의 한 줄을 내려주는 DTO Record입니다.
 * 본문 스크립트를 제외해 목록 응답을 가볍게 유지합니다.
 * [사용 위치] MeditationService → MeditationListResponse 에 담김
 */
public record MeditationSummaryResponse(
        Long meditationId,
        String title,
        String category,            // meditation.category_no 를 문자열로 내려준다
        Integer durationSeconds
) {
}
