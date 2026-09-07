package com.templestamp.manuscript;

import com.templestamp.global.response.ApiResponse;
import com.templestamp.global.response.PageResponse;
import com.templestamp.global.security.AuthenticatedUser;
import com.templestamp.manuscript.dto.ManuscriptCreateRequest;
import com.templestamp.manuscript.dto.ManuscriptResponse;
import com.templestamp.manuscript.dto.ManuscriptUpdateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 편집자 문. 원고를 쓰고 고치고 낸다 — <b>심사는 못 한다</b>(그쪽은 /api/admin).
 * 관리자도 이 문을 쓸 수 있다(원고를 쓸 수 있어야 하므로). 다만 자기 원고는 자기가 승인하지 못한다.
 */
@RestController
@RequestMapping("/api/editor/manuscripts")
@RequiredArgsConstructor
@Validated
public class EditorManuscriptController {

    private final ManuscriptService manuscriptService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ManuscriptResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @Valid @RequestBody ManuscriptCreateRequest request) {
        return ApiResponse.ok(manuscriptService.create(user.userId(), request));
    }

    /** 제목·본문만. 사찰·구·종류는 변형 키라 바꿀 수 없다 — 바꾸려면 새로 등록한다. */
    @PutMapping("/{manuscriptId}")
    public ApiResponse<ManuscriptResponse> update(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @PathVariable @Positive Long manuscriptId,
                                                  @Valid @RequestBody ManuscriptUpdateRequest request) {
        return ApiResponse.ok(
                manuscriptService.update(user.userId(), manuscriptId, request.title(), request.body()));
    }

    @PostMapping("/{manuscriptId}/submit")
    public ApiResponse<Void> submit(@AuthenticationPrincipal AuthenticatedUser user,
                                    @PathVariable @Positive Long manuscriptId) {
        manuscriptService.submit(user.userId(), manuscriptId);
        return ApiResponse.ok();
    }

    /**
     * 내 원고 목록. 편집자는 <b>자기 것만</b> 본다 — 남의 초안과 반려 사유는 남의 것이다.
     * 관리자는 전체를 본다(심사해야 하므로).
     */
    @GetMapping
    public ApiResponse<PageResponse<ManuscriptResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @Positive Long siteId,
            @RequestParam(required = false) @Min(1) @Max(5) Integer verseNo,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Long authorFilter = user.isAdmin() ? null : user.userId();
        return ApiResponse.ok(manuscriptService.list(authorFilter, siteId, verseNo, kind, status, page, size));
    }
}
