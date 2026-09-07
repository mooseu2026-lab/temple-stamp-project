package com.templestamp.ebook;

import com.templestamp.ebook.dto.PrintOrderRequest;
import com.templestamp.ebook.dto.PrintOrderDetailResponse;
import com.templestamp.ebook.dto.PrintOrderResponse;
import com.templestamp.global.response.ApiResponse;
import com.templestamp.global.response.ItemsResponse;
import com.templestamp.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회향본 인쇄 신청. 응답의 연락처·주소는 마스킹돼 나간다.
 */
@Validated
@RestController
@RequestMapping("/api/print-orders")
@RequiredArgsConstructor
public class PrintOrderController {

    private final PrintOrderService printOrderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PrintOrderResponse> order(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @Valid @RequestBody PrintOrderRequest request) {
        return ApiResponse.ok(printOrderService.order(user.userId(), request));
    }

    @GetMapping
    public ApiResponse<ItemsResponse<PrintOrderResponse>> getMine(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(ItemsResponse.of(printOrderService.getMine(user.userId())));
    }

    @GetMapping("/{printOrderId}")
    public ApiResponse<PrintOrderDetailResponse> get(@AuthenticationPrincipal AuthenticatedUser user,
                                               @PathVariable Long printOrderId) {
        return ApiResponse.ok(printOrderService.get(user.userId(), printOrderId));
    }

    /** 인쇄가 시작되기 전에만 취소할 수 있다. */
    /** 취소는 본문이 없다 — 204. 남길 말이 없는 응답에 빈 봉투를 실을 이유가 없다(탈퇴와 같다). */
    @DeleteMapping("/{printOrderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal AuthenticatedUser user,
                                    @PathVariable Long printOrderId) {
        printOrderService.cancel(user.userId(), printOrderId);
    }
}
