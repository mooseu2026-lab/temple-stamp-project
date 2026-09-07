package com.templestamp.course.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 코스 정보에 권역명을 조인해 담는 조회 전용 DTO 클래스입니다.
 * [사용 위치] CourseMapper 의 resultType (course JOIN region)
 */
@Getter
@Setter
@NoArgsConstructor
public class CourseRow {
    private Long courseId;
    private Long regionId;
    private String regionName;    // region.name 조인
    private String name;
    private String description;
    private String status;        // ACTIVE 만 사용자에게 노출
}
