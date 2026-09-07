// src/main/java/com/templestamp/admin/dto/SiteBadgeSaveRequest.java
package com.templestamp.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 뱃지 1건 UPSERT — uk_site_badge(site_id, badge_type) */
public record SiteBadgeSaveRequest(
        @NotBlank @Pattern(regexp = "^(FLOWER|GUARDIAN)$", message = "뱃지는 FLOWER 또는 GUARDIAN 입니다.") String badgeType,
        @Size(max = 500) String description
) {}
