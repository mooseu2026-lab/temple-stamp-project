package com.templestamp.course.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 권역과 그 권역에 속한 코스 개수를 함께 담는 조회 전용 DTO 클래스입니다.
 * courseCount 는 region 테이블에 없는 집계값이라 도메인 클래스로는 받을 수 없습니다.
 * [사용 위치] RegionMapper 의 resultType (region LEFT JOIN course COUNT)
 */
@Getter
@Setter
@NoArgsConstructor
public class RegionRow {
    private Long regionId;
    private String code;          // JEJU 등 영문 코드. 프론트가 숫자 id 대신 이걸로 분기한다
    private String name;
    private Integer sortNo;       // 권역 노출 순서
    private Integer courseCount;  // COUNT(course) — 집계값
}
