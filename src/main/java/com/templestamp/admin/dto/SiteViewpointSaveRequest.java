// src/main/java/com/templestamp/admin/dto/SiteViewpointSaveRequest.java
package com.templestamp.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 뷰포인트 1건 UPSERT — uk_site_viewpoint(site_id, sort_no). 같은 sortNo 로 다시 보내면 덮어쓴다 */
public record SiteViewpointSaveRequest(
        @NotNull @Min(1) @Max(3) Integer sortNo,                     // DB CHECK (1~3) 미러링
        @NotBlank @Size(max = 255) String locationDesc,
        @Size(max = 100) String bestTime,
        @Size(max = 255) String whatToSee
) {}
