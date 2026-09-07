package com.templestamp.admin;

import com.templestamp.admin.dto.AdminEbookFileRequest;
import com.templestamp.admin.dto.AdminPrintOrderResponse;
import com.templestamp.admin.dto.PrintOrderStatusRequest;
import com.templestamp.ebook.EbookService;
import com.templestamp.ebook.PrintOrderService;
import com.templestamp.ebook.dto.PrintOrderRow;
import com.templestamp.global.response.ApiResponse;
import com.templestamp.global.response.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 전자책 파일 등록과 회향본 주문 관리.
 * <p>
 * 여기서 내려가는 주문 정보는 마스킹하지 않는다. 송장을 뽑아야 하기 때문이다.
 * 그래서 이 경로는 ADMIN 권한이 필수이고(SecurityConfig), 사용자용 응답 타입과 분리돼 있다.
 */
@Validated
@RestController
@RequestMapping("/api/admin/ebooks")
@RequiredArgsConstructor
public class AdminEbookController {

    private final EbookService ebookService;
    private final PrintOrderService printOrderService;

    /** 외부 조판이 끝난 뒤 결과 파일 키를 등록한다. 등록되면 상태가 READY 로 바뀐다. */
    @PutMapping("/{ebookId}/files")
    public ApiResponse<Void> registerFiles(@PathVariable Long ebookId,
                                           @Valid @RequestBody AdminEbookFileRequest request) {
        ebookService.registerFiles(ebookId, request.pdfKey(), request.epubKey());
        return ApiResponse.ok();
    }

    @GetMapping("/print-orders")
    public ApiResponse<PageResponse<AdminPrintOrderResponse>> getPrintOrders(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        PageResponse<PrintOrderRow> rows = printOrderService.getAllForAdmin(status, page, size);

        return ApiResponse.ok(PageResponse.of(
                rows.items().stream()
                        .map(r -> AdminPrintOrderResponse.of(r,
                                printOrderService.addressOf(r.getPrintOrderId())))
                        .toList(),
                rows.page(), rows.size(), rows.totalCount()));
    }

    @PatchMapping("/print-orders/{printOrderId}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long printOrderId,
                                          @Valid @RequestBody PrintOrderStatusRequest request) {
        printOrderService.updateStatus(printOrderId, request.status(),
                request.trackingNo(), request.reason());
        return ApiResponse.ok();
    }
}
