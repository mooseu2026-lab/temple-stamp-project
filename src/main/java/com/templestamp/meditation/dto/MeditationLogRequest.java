package com.templestamp.meditation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 명상을 얼마나 재생했는지와 감상 메모를 전달받는 DTO Record입니다.
 * [사용 위치] MeditationController — @RequestBody @Valid
 */
public record MeditationLogRequest(

        @NotNull(message = "명상 번호가 필요합니다.")
        @Positive(message = "명상 번호가 올바르지 않습니다.")
        Long meditationId,

        @NotNull(message = "재생 시간이 필요합니다.")
        @PositiveOrZero(message = "재생 시간이 올바르지 않습니다.")
        Integer playedSeconds,

        @Size(max = 300, message = "감상 메모는 300자 이하로 입력해주세요.")
        String memo               // 선택
) {
}
