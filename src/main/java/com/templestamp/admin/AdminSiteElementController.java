// src/main/java/com/templestamp/admin/AdminSiteElementController.java
package com.templestamp.admin;

import org.springframework.http.HttpStatus;
import com.templestamp.admin.dto.SiteElementSaveRequest;
import com.templestamp.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사찰의 참배 요소(7자리) 관리 — ADMIN. 「가는 법」 응답이 곧바로 바뀐다.
 * AdminSiteController 와 같은 경로 아래지만 파일을 나눈 것은 QR·상태·i18n 과 관심사가 달라서다.
 */
@RestController
@RequestMapping("/api/admin/sites")
@RequiredArgsConstructor
@Validated
public class AdminSiteElementController {

    private final AdminSiteService adminSiteService;

    @PostMapping("/{siteId}/elements")
    public ApiResponse<Void> upsert(@PathVariable @Positive Long siteId,
                                    @RequestBody @Valid SiteElementSaveRequest req) {
        adminSiteService.upsertElements(siteId, req);
        return ApiResponse.ok();
    }

    /**
     * 삭제는 <b>204 · 본문 없음</b>이다. DELETE 다섯 중 둘만 204 였고 셋은 200 + 봉투였다 —
     * 같은 행위가 자리마다 다른 답을 내면 프론트가 자리마다 다르게 분기한다(감사 G STEP 5).
     */
    @DeleteMapping("/{siteId}/elements/{elementCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable @Positive Long siteId,
                       @PathVariable String elementCode) {
        adminSiteService.deleteElement(siteId, elementCode);
    }
}
