package com.templestamp.manuscript.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 원고 등록. 사찰·구·종류는 <b>변형 키</b>라 등록 뒤에는 바꿀 수 없다 — 바꾸려면 새로 등록한다.
 * 길이 상한이 종류마다 달라(미션 2000 · 확장문구 200) 여기서는 큰 쪽만 막고 나머지는 서비스가 본다.
 * [사용 위치] EditorManuscriptController — @RequestBody @Valid
 */
public record ManuscriptCreateRequest(

        /** 비우면 기본 원고(구·종류마다 한 편). 사찰 status 는 묻지 않는다 — DRAFT 사찰에도 미리 쓴다. */
        @Positive(message = "사찰 번호가 올바르지 않습니다.")
        Long siteId,

        @NotNull(message = "구절 번호가 필요합니다.")
        @Min(value = 1, message = "구절 번호는 1~5 입니다.")
        @Max(value = 5, message = "구절 번호는 1~5 입니다.")
        Integer verseNo,

        @NotBlank(message = "원고 종류가 필요합니다.")
        @Pattern(regexp = "^(MISSION|EXT)$", message = "종류는 MISSION 또는 EXT 입니다.")
        String kind,

        @NotBlank(message = "제목을 입력해주세요.")
        @Size(max = 50, message = "제목은 50자 이하로 입력해주세요.")
        String title,

        @NotBlank(message = "본문을 입력해주세요.")
        @Size(max = 2000, message = "본문은 2000자 이하로 입력해주세요.")
        String body
) {
}
