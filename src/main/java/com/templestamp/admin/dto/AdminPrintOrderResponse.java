package com.templestamp.admin.dto;

import com.templestamp.ebook.dto.PrintOrderAddress;
import com.templestamp.ebook.dto.PrintOrderRow;

import java.time.LocalDateTime;

/**
 * 관리자에게 신청 내역을 내려주는 DTO Record입니다.
 * 실제 배송이 필요하므로 연락처와 주소를 마스킹하지 않습니다. (ADMIN 권한 경로 전용)
 * 사용자용 PrintOrderResponse 와 타입을 나눈 이유가 이것이다 — 한 타입에 플래그를 두면
 * 언젠가 그 플래그가 잘못 켜져 사용자 화면으로 원본이 새어 나간다.
 * <p>
 * 챕터 9 부터 주소는 {@code print_order_address} 에서 온다. 본체 질의는 주소를 읽지 않으므로,
 * 이 응답을 만들 때 <b>일부러 한 번 더 읽어야</b> 주소가 실린다 — 새어 나가려면 손이 가야 한다.
 * [사용 위치] AdminEbookController — 관리자 인쇄 신청 목록
 */
public record AdminPrintOrderResponse(
        Long printOrderId,
        Long userId,
        String nickname,
        Long ebookId,
        String ebookType,
        Integer quantity,
        String recipientName,
        String recipientPhone,        // 마스킹 없음
        String postalCode,
        String address,               // 마스킹 없음
        String status,
        String note,
        String trackingNo,
        String cancelReason,
        boolean needsReview,          // 탈퇴했는데 실물이 움직이는 주문 — 목록 맨 위로 온다
        LocalDateTime requestedAt,
        LocalDateTime updatedAt
) {
    public static AdminPrintOrderResponse of(PrintOrderRow row, PrintOrderAddress a) {
        return new AdminPrintOrderResponse(row.getPrintOrderId(), row.getUserId(), row.getNickname(),
                row.getEbookId(), row.getEbookType(), row.getQuantity(),
                a == null ? null : a.getRecipient(),
                a == null ? null : a.getPhone(),
                a == null ? null : a.getPostalCode(),
                a == null ? null : a.getAddress(),
                row.getStatus(), row.getNote(), row.getTrackingNo(), row.getCancelReason(),
                Boolean.TRUE.equals(row.getNeedsReview()),
                row.getRequestedAt(), row.getUpdatedAt());
    }
}
