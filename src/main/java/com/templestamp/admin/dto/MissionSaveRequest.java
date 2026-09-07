package com.templestamp.admin.dto;

import com.templestamp.global.type.Tier;
import com.templestamp.verse.MissionScope;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 현장 미션 1편을 등록·수정하는 요청 DTO Record입니다. variantNo 를 비우면 Service 가 채번합니다.
 * [사용 위치] AdminContentController — @RequestBody @Valid
 */
public record MissionSaveRequest(

        /** VERSE 과제만 필요하다. SITE·COMMON 은 비운다. */
        @Min(value = 1, message = "구절 번호는 1~5 입니다.")
        @Max(value = 5, message = "구절 번호는 1~5 입니다.")
        Integer verseNo,

        @NotNull(message = "대상 계층이 필요합니다.")
        Tier tier,

        /** 출처 축. 비우면 VERSE 로 본다 — 챕터 5 부터 있던 과제와 같은 뜻이다. */
        MissionScope scope,

        /** SITE 과제만 필요하다. */
        Long siteId,

        @Min(value = 1, message = "변형 번호는 1 이상입니다.")
        Integer variantNo,

        @NotBlank(message = "미션 내용을 입력해주세요.")
        @Size(max = 300, message = "미션은 300자 이하로 입력해주세요.")
        String body,

        @Size(max = 20)
        String originRef,

        String reviewStatus
) {
}
