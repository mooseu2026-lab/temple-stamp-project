package com.templestamp.ebook.dto;

import com.templestamp.ebook.PrintOrder;

import java.time.LocalDateTime;

/**
 * 인쇄 주문 목록 한 건(챕터 9 §4).
 * <p>
 * <b>배송정보가 없다.</b> 주소는 {@code print_order_address} 에 따로 있고 목록 질의는 그 표를 읽지 않는다 —
 * 응답에서 지우는 것이 아니라 <b>애초에 가져오지 않는다</b>. 지우는 방식은 필드를 하나 더할 때
 * 다시 지우는 것을 잊으면 그대로 새어 나간다(보상 목록과 같은 원칙 · 보안 S16).
 * [사용 위치] PrintOrderService → Controller
 */
public record PrintOrderResponse(
        Long printOrderId,
        Long ebookId,
        String ebookType,
        Integer quantity,
        String status,
        String trackingNo,            // SHIPPED 전이면 null
        String cancelReason,          // 취소가 아니면 null
        /**
         * 지금 이 주문을 <b>사용자가</b> 취소할 수 있는가. 서버가 계산해 준다(챕터 10 · 프론트 요구 FE-1).
         * <p>
         * 프론트가 {@code status == "REQUESTED"} 로 판단하면 그 규칙이 두 곳에 살게 된다 —
         * 상태가 하나 늘거나 취소 조건이 바뀌는 날 화면만 옛 규칙으로 남는다.
         * 관리자 응답에는 이 필드가 없다. 관리자의 취소는 CONFIRMED 까지 되는 다른 규칙이다.
         */
        boolean cancelable,
        LocalDateTime requestedAt,
        LocalDateTime updatedAt
) {
    public static PrintOrderResponse from(PrintOrderRow row) {
        return new PrintOrderResponse(row.getPrintOrderId(), row.getEbookId(), row.getEbookType(),
                row.getQuantity(), row.getStatus(), row.getTrackingNo(), row.getCancelReason(),
                PrintOrder.REQUESTED.equals(row.getStatus()),
                row.getRequestedAt(), row.getUpdatedAt());
    }
}
