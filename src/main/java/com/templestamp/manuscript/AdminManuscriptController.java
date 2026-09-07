package com.templestamp.manuscript;

import com.templestamp.global.response.ApiResponse;
import com.templestamp.global.response.PageResponse;
import com.templestamp.global.security.AuthenticatedUser;
import com.templestamp.manuscript.dto.ImportResultResponse;
import com.templestamp.manuscript.dto.ManuscriptRejectRequest;
import com.templestamp.manuscript.dto.ManuscriptResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 원고 심사. 승인·반려·퇴역과 CSV 일괄 반입.
 * <p>
 * <b>지우는 문은 없다.</b> 잘못 들어온 원고도 퇴역시킬 뿐이다 — 이미 그 원고를 참조하는 도장이 있으면
 * 지우는 순간 그 사람의 기록에서 글이 사라진다.
 */
@RestController
@RequestMapping("/api/admin/manuscripts")
@RequiredArgsConstructor
@Validated
public class AdminManuscriptController {

    private final ManuscriptService manuscriptService;
    private final ManuscriptImportService manuscriptImportService;

    /** 심사 목록. 오래 기다린 제출이 먼저 온다. */
    @GetMapping
    public ApiResponse<PageResponse<ManuscriptResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @Positive Long siteId,
            @RequestParam(required = false) @Min(1) @Max(5) Integer verseNo,
            @RequestParam(required = false) String kind,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(manuscriptService.list(null, siteId, verseNo, kind, status, page, size));
    }

    /** 승인. 자기가 쓴 원고는 승인할 수 없다(4-eyes) — 검토가 이름만 남지 않게. */
    @PostMapping("/{manuscriptId}/approve")
    public ApiResponse<Void> approve(@AuthenticationPrincipal AuthenticatedUser admin,
                                     @PathVariable @Positive Long manuscriptId) {
        manuscriptService.approve(admin.userId(), manuscriptId);
        return ApiResponse.ok();
    }

    @PostMapping("/{manuscriptId}/reject")
    public ApiResponse<Void> reject(@AuthenticationPrincipal AuthenticatedUser admin,
                                    @PathVariable @Positive Long manuscriptId,
                                    @Valid @RequestBody ManuscriptRejectRequest request) {
        manuscriptService.reject(admin.userId(), manuscriptId, request.reason());
        return ApiResponse.ok();
    }

    @PostMapping("/{manuscriptId}/retire")
    public ApiResponse<Void> retire(@AuthenticationPrincipal AuthenticatedUser admin,
                                    @PathVariable @Positive Long manuscriptId) {
        manuscriptService.retire(admin.userId(), manuscriptId);
        return ApiResponse.ok();
    }

    /**
     * CSV 일괄 반입. {@code dryRun=true} 면 검증만 하고 한 줄도 넣지 않는다 —
     * 140편을 올리기 전에 이걸 먼저 돌리라는 뜻이다.
     */
    @PostMapping("/import")
    public ApiResponse<ImportResultResponse> importCsv(@AuthenticationPrincipal AuthenticatedUser admin,
                                                       @RequestPart("file") MultipartFile file,
                                                       @RequestParam(defaultValue = "false") boolean dryRun) {
        return ApiResponse.ok(manuscriptImportService.importCsv(admin.userId(), file, dryRun));
    }
}
