package com.templestamp.manuscript.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 원고 수정 — 제목·본문만. 사찰·구·종류는 변형 키라 바꿀 수 없다(바꾸면 다른 자리의 원고가 된다).
 * [사용 위치] EditorManuscriptController — @RequestBody @Valid
 */
public record ManuscriptUpdateRequest(

        @NotBlank(message = "제목을 입력해주세요.")
        @Size(max = 50, message = "제목은 50자 이하로 입력해주세요.")
        String title,

        @NotBlank(message = "본문을 입력해주세요.")
        @Size(max = 2000, message = "본문은 2000자 이하로 입력해주세요.")
        String body
) {
}
