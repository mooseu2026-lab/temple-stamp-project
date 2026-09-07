package com.templestamp.course.dto;

/**
 * 9개 권역 목록을 내려주는 DTO Record입니다.
 * 권역 카드에 코스 개수를 함께 표시하기 위한 값을 담습니다.
 * <p>
 * code(JEJU 등)를 함께 내리는 이유 — 프론트가 지도 9권역을 점등할 때 숫자 id 를 하드코딩하지 않아도 되게.
 * id 는 시드 순서에 따라 바뀔 수 있지만 code 는 바뀌지 않는다.
 * [사용 위치] CourseService 가 RegionRow → 변환 → Controller 반환
 */
public record RegionResponse(
        Long regionId,
        String code,
        String name,
        Integer courseCount
) {
    public static RegionResponse from(RegionRow row) {
        return new RegionResponse(row.getRegionId(), row.getCode(), row.getName(), row.getCourseCount());
    }
}
