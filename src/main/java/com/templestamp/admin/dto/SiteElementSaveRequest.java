// src/main/java/com/templestamp/admin/dto/SiteElementSaveRequest.java
package com.templestamp.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 사찰의 7자리 중 "있는 것" 만 보낸다. 보내지 않은 자리는 건드리지 않는다(null=변경 없음).
 * 없앨 때는 DELETE /elements/{code}.
 */
public record SiteElementSaveRequest(@NotEmpty @Size(max = 7) List<@Valid Item> items) {
    public record Item(
            @NotBlank @Size(max = 30) String elementCode,   // ILJUMUN 등 shrine_element.code
            @NotBlank @Size(max = 100) String localName,    // 그 절에서 부르는 이름
            @Size(max = 255) String note) {}
}
