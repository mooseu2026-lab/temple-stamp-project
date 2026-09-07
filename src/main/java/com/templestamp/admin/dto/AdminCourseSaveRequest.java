package com.templestamp.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 코스 등록·수정 요청 DTO Record입니다. 코스는 반드시 사찰 5곳으로 구성됩니다.
 * position 1~5 중복 없음, verseNo 1~5 가 한 번씩 배정되는지는 Service 가 검증합니다.
 * [사용 위치] AdminCourseController — @RequestBody @Valid
 */
public record AdminCourseSaveRequest(

        @NotNull(message = "권역을 선택해주세요.")
        @Positive(message = "권역 번호가 올바르지 않습니다.")
        Long regionId,

        @NotBlank(message = "코스 이름을 입력해주세요.")
        @Size(max = 100, message = "코스 이름은 100자 이하로 입력해주세요.")
        String name,

        String description,

        @Pattern(regexp = "^(DRAFT|ACTIVE|INACTIVE)$", message = "지원하지 않는 상태입니다.")
        String status,

        Integer sortNo,

        @NotNull(message = "사찰 구성이 필요합니다.")
        @Size(min = 5, max = 5, message = "코스는 사찰 5곳으로 구성해야 합니다.")
        List<@Valid SiteSlot> sites
) {

    public record SiteSlot(

            @NotNull(message = "사찰이 필요합니다.")
            @Positive(message = "사찰 번호가 올바르지 않습니다.")
            Long siteId,

            @NotNull(message = "순번이 필요합니다.")
            @Min(value = 1, message = "순번은 1~5 입니다.")
            @Max(value = 5, message = "순번은 1~5 입니다.")
            Integer position,

            @NotNull(message = "담당 구절이 필요합니다.")
            @Min(value = 1, message = "구절 번호는 1~5 입니다.")
            @Max(value = 5, message = "구절 번호는 1~5 입니다.")
            Integer verseNo
    ) {}
}
