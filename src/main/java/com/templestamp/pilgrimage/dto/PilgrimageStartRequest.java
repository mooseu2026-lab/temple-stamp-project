package com.templestamp.pilgrimage.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 어느 코스의 순례를 시작할지 전달받는 DTO Record입니다.
 * [사용 위치] PilgrimageController — @RequestBody @Valid
 */
public record PilgrimageStartRequest(

        @NotNull(message = "코스를 선택해주세요.")
        @Positive(message = "코스 번호가 올바르지 않습니다.")
        Long courseId
) {
}
