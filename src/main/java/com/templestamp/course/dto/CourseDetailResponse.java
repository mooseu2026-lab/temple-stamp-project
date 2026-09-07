package com.templestamp.course.dto;

import java.util.List;

/**
 * 코스 상세 화면 전체를 한 번에 내려주는 DTO Record입니다.
 * 5개 사찰 좌표를 일괄로 담아 산중에서 네트워크를 다시 타지 않게 합니다.
 * [사용 위치] CourseService 조립 → Controller 반환
 */
public record CourseDetailResponse(
        Long courseId,
        Long regionId,
        String regionName,
        String name,
        String description,
        ProgressResponse progress,          // 비로그인이면 null
        List<CourseSiteResponse> sites      // position 순 5건
) {
}
