package com.templestamp.meditation.dto;

import java.util.List;

/**
 * 명상 목록과 카테고리별 개수를 함께 내려주는 DTO Record입니다. 로그인 없이 조회 가능.
 * [사용 위치] MeditationService 조립 → Controller 반환
 */
public record MeditationListResponse(
        List<MeditationSummaryResponse> items,
        List<CategoryCount> categoryCounts
) {
    public record CategoryCount(String category, int count) {}
}
