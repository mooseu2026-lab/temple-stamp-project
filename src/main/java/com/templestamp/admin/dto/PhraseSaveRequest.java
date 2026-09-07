package com.templestamp.admin.dto;

import com.templestamp.global.type.Tier;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 확장문구 1편을 등록·수정하는 요청 DTO Record입니다.
 * (구절 5 × tier 7 × 버전 5 = 175편 풀 구조. versionNo 를 비우면 Service 가 다음 번호를 채번)
 * [사용 위치] AdminContentController — @RequestBody @Valid
 */
public record PhraseSaveRequest(

        @NotNull(message = "대상 구절이 필요합니다.")
        @Min(value = 1, message = "구절 번호는 1~5 입니다.")
        @Max(value = 5, message = "구절 번호는 1~5 입니다.")
        Integer verseNo,

        @NotNull(message = "대상 계층이 필요합니다.")
        Tier tier,

        /** 비우면 그 (구절·계층) 의 다음 빈 버전에 넣는다. 주면 그 버전을 덮어쓴다. */
        @Min(value = 1, message = "버전은 1~5 입니다.")
        @Max(value = 5, message = "버전은 1~5 입니다.")
        Integer versionNo,

        @NotBlank(message = "문구 내용을 입력해주세요.")
        @Size(max = 500, message = "문구는 500자 이하로 입력해주세요.")
        String textKo,

        String reviewStatus
) {
}
