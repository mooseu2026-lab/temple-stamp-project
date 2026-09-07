package com.templestamp.thinkbox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 생각상자에 직접 기록을 작성할 때 본문과 연결할 코스·사찰을 전달받는 DTO Record입니다.
 * courseId·siteId 는 선택 — 어디에도 안 묶인 자유 기록도 허용.
 * [사용 위치] ThinkboxController — @RequestBody @Valid
 */
public record ThinkboxCreateRequest(

        @NotBlank(message = "내용을 입력해주세요.")
        @Size(max = 300, message = "내용은 300자 이하로 입력해주세요.")
        String content,

        @Positive(message = "코스 번호가 올바르지 않습니다.")
        Long courseId,

        @Positive(message = "사찰 번호가 올바르지 않습니다.")
        Long siteId,

        /** 비공개 여부. 안 보내면 비공개로 만든다 — 공개는 사용자가 따로 켜는 것이다. */
        Boolean isPrivate
) {
}
