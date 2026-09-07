package com.templestamp.admin;

import com.templestamp.admin.dto.PendingStampResponse;
import com.templestamp.admin.dto.StampReviewRequest;
import com.templestamp.global.response.ApiResponse;
import com.templestamp.global.response.PageResponse;
import com.templestamp.global.security.AuthenticatedUser;
import com.templestamp.stamp.StampService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PENDING 스탬프 심사. 이동시간 미달(TRAVEL_TIME)과 예외 접수(EVIDENCE)가 함께 올라온다.
 */
@Validated
@RestController
@RequestMapping("/api/admin/stamps")
@RequiredArgsConstructor
public class AdminStampController {

    private final StampService stampService;

    @GetMapping("/pending")
    public ApiResponse<PageResponse<PendingStampResponse>> getPending(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(stampService.getPending(page, size));
    }

    /** APPROVE → COMPLETED 확정 + 완주 재집계 / REJECT → REJECTED 종단(사유 필수). */
    @PostMapping("/{stampId}/review")
    public ApiResponse<Void> review(@AuthenticationPrincipal AuthenticatedUser admin,
                                    @PathVariable Long stampId,
                                    @Valid @RequestBody StampReviewRequest request) {
        stampService.review(admin.userId(), stampId, request);
        return ApiResponse.ok();
    }
}
