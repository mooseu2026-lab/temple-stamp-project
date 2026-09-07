package com.templestamp.stamp.dto;

import com.templestamp.global.type.AccuracyGrade;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 인증 1단계에서 단말이 판정한 반경 내 여부와 정확도 등급만 전달받는 DTO Record입니다.
 * 위도·경도 필드가 정의 자체에 없으며, 좌표가 들어오면 필터가 400 으로 거부합니다.
 * (대상 사찰 courseSiteId 는 URL 경로 변수로 받음)
 * [사용 위치] StampController — @RequestBody @Valid. LOW 거부는 Service 판단
 */
public record GpsCheckRequest(

        /**
         * v4 — 이 자리(구)에서 실제로 서 있는 후보 사찰. 한 자리에 후보가 여럿이라
         * course_site.site_id(대표)만으로는 어디에서 찍었는지 알 수 없다.
         * 이 자리의 후보가 아니면 400 COURSE-4001.
         */
        @NotNull(message = "사찰이 필요합니다.")
        @Positive(message = "사찰 번호가 올바르지 않습니다.")
        Long siteId,

        @NotNull(message = "반경 판정 결과가 필요합니다.")
        Boolean withinRadius,

        @NotNull(message = "정확도 등급이 필요합니다.")
        AccuracyGrade accuracyGrade      // HIGH·MID·LOW 외 값은 Jackson 이 400
) {
}
