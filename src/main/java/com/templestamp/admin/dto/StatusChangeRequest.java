// src/main/java/com/templestamp/admin/dto/StatusChangeRequest.java
package com.templestamp.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 사찰·코스 공용 상태 변경 요청. DRAFT→ACTIVE→INACTIVE, INACTIVE→ACTIVE 허용. ACTIVE 조건은 Service 가 검사 */
public record StatusChangeRequest(
        @NotBlank(message = "상태를 입력해주세요.")
        @Pattern(regexp = "^(DRAFT|ACTIVE|INACTIVE)$", message = "상태는 DRAFT, ACTIVE, INACTIVE 중 하나여야 합니다.")
        String status
) {}
