package com.templestamp.course.dto;

/**
 * 코스 목록 한 건을 내려주는 DTO Record입니다.
 * 로그인 사용자에게는 진행률을 결합해 주고, 비로그인이면 progress 가 null 로 나갑니다.
 * [사용 위치] CourseService 가 CourseRow + ProgressResponse 결합 → Controller 반환
 */
public record CourseResponse(
        Long courseId,
        Long regionId,
        String regionName,
        String name,
        ProgressResponse progress     // 비로그인·미시작이면 null
) {
    public static CourseResponse of(CourseRow row, ProgressResponse progress) {
        return new CourseResponse(row.getCourseId(), row.getRegionId(),
                row.getRegionName(), row.getName(), progress);
    }
}
