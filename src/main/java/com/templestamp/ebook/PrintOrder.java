package com.templestamp.ebook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * print_order 테이블 도메인 클래스 — 회향본 소량 인쇄 신청.
 * 사용자-전자책 조합은 유일하다(uk_print_user_ebook 은 없지만 서비스가 중복을 막는다).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintOrder {

    public static final String REQUESTED = "REQUESTED";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String PRINTING = "PRINTING";   // 챕터 9 — 여기부터는 취소가 없다
    public static final String SHIPPED = "SHIPPED";
    public static final String DONE = "DONE";
    public static final String CANCELED = "CANCELED";

    private Long printOrderId;
    private Long userId;
    private Long ebookId;
    private Integer quantity;
    // 배송정보는 print_order_address 로 뗐다(챕터 9 §4) — 목록 질의가 본체만 읽게 하려고.
    private String status;
    private String note;
    private String trackingNo;
    private String cancelReason;      // 관리자 취소는 사유 필수
    private Boolean needsReview;      // 탈퇴했는데 실물이 이미 움직이는 주문
    private LocalDateTime requestedAt;
    private LocalDateTime updatedAt;

    /** 신청 직후에만 사용자가 취소할 수 있다. 인쇄가 시작되면 되돌릴 수 없다. */
    /** 사용자 취소는 REQUESTED 에서만. 확인이 떨어진 뒤에는 인쇄가 걸려 있다. */
    public boolean isCancelable() { return REQUESTED.equals(status); }

    /** 관리자 취소는 확인까지. PRINTING 부터는 종이가 이미 나가고 있어 되돌릴 수 없다. */
    public boolean isAdminCancelable() {
        return REQUESTED.equals(status) || CONFIRMED.equals(status);
    }
}
