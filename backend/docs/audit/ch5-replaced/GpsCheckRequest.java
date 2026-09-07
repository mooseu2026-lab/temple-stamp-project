package com.templestamp.stamp.dto;

import com.templestamp.global.type.AccuracyGrade;
import jakarta.validation.constraints.NotNull;

/**
 * 인증 1단계에서 단말이 판정한 반경 내 여부와 정확도 등급만 전달받는 DTO Record입니다.
 * 위도·경도 필드가 정의 자체에 없으며, 좌표가 들어오면 필터가 400 으로 거부합니다.
 * (대상 사찰 courseSiteId 는 URL 경로 변수로 받음)
 * [사용 위치] StampController — @RequestBody @Valid. LOW 거부는 Service 판단
 */
public record GpsCheckRequest(

        @NotNull(message = "반경 판정 결과가 필요합니다.")
        Boolean withinRadius,

        @NotNull(message = "정확도 등급이 필요합니다.")
        AccuracyGrade accuracyGrade      // HIGH·MID·LOW 외 값은 Jackson 이 400
) {
}
