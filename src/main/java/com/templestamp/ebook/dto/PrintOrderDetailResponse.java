package com.templestamp.ebook.dto;

import com.templestamp.ebook.PrintOrder;

import java.time.LocalDateTime;

/**
 * 인쇄 주문 1건 조회(챕터 9 §4). <b>본인만</b> 볼 수 있고 배송정보가 함께 실린다.
 * <p>
 * 목록({@link PrintOrderResponse})과 굳이 나눈 이유 — 목록은 주소를 <b>가져오지 않고</b>,
 * 단건은 가져온다. 한 DTO 에 null 로 섞어 두면 "언제 채워지는가" 를 호출부마다 다시 물어야 한다.
 */
public record PrintOrderDetailResponse(
        Long printOrderId,
        Long ebookId,
        String ebookType,
        Integer quantity,
        String status,
        String trackingNo,
        String cancelReason,
        /**
         * 지금 이 주문을 <b>사용자가</b> 취소할 수 있는가. 서버가 계산해 준다(챕터 10 · 프론트 요구 FE-1).
         * <p>
         * 프론트가 {@code status == "REQUESTED"} 로 판단하면 그 규칙이 두 곳에 살게 된다 —
         * 상태가 하나 늘거나 취소 조건이 바뀌는 날 화면만 옛 규칙으로 남는다.
         * 관리자 응답에는 이 필드가 없다. 관리자의 취소는 CONFIRMED 까지 되는 다른 규칙이다.
         */
        boolean cancelable,
        LocalDateTime requestedAt,
        LocalDateTime updatedAt,
        Shipping shipping
) {
    /** 배송정보. 본인 조회라 마스킹하지 않는다 — 자기 주소를 가려 주는 것은 도움이 되지 않는다. */
    public record Shipping(String recipient, String phone, String postalCode,
                           String address, String memo) {
    }

    public static PrintOrderDetailResponse of(PrintOrderRow row, PrintOrderAddress a) {
        Shipping shipping = a == null ? null
                : new Shipping(a.getRecipient(), a.getPhone(), a.getPostalCode(),
                        a.getAddress(), a.getMemo());
        return new PrintOrderDetailResponse(row.getPrintOrderId(), row.getEbookId(), row.getEbookType(),
                row.getQuantity(), row.getStatus(), row.getTrackingNo(), row.getCancelReason(),
                PrintOrder.REQUESTED.equals(row.getStatus()),
                row.getRequestedAt(), row.getUpdatedAt(), shipping);
    }
}
