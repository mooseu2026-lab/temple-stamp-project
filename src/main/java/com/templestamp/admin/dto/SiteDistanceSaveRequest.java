// src/main/java/com/templestamp/admin/dto/SiteDistanceSaveRequest.java
package com.templestamp.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/** PUT /api/admin/site-distances 본문. 한 번에 최대 240행(60곳 기준 전체). siteAId ≠ siteBId 는 DB CHECK + Service 가 함께 검사 */
public record SiteDistanceSaveRequest(
        @NotEmpty @Size(max = 240) List<@Valid Item> items
) {
    public record Item(
            @NotNull @Positive Long siteAId,                 // 직전 사찰
            @NotNull @Positive Long siteBId,                 // 다음 사찰
            @NotNull @Min(1) @Max(1440) Integer minMinutes   // 1분~하루. 이보다 빨리 오면 챕터 5 가 PENDING(TRAVEL_TIME)
    ) {}
}
