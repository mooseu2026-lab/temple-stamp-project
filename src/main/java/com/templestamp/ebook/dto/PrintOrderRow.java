package com.templestamp.ebook.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 인쇄 신청에 전자책과 사용자 닉네임을 조인해 담는 조회 전용 DTO 클래스입니다.
 * [사용 위치] PrintOrderMapper(ebook 패키지) 의 resultType (print_order JOIN ebook·users)
 */
@Getter
@Setter
@NoArgsConstructor
public class PrintOrderRow {
    private Long printOrderId;
    private Long userId;
    private String nickname;          // users.nickname 조인 — 관리자 응답용
    private Long ebookId;
    private String ebookType;         // ebook 조인
    private Integer quantity;
    // 배송정보는 여기 없다. 목록 질의가 주소를 읽지 않으면 목록으로 새어 나갈 길 자체가 없다
    // (reward_claim 과 같은 원칙 — 보안 S16).
    private String status;            // REQUESTED / CONFIRMED / PRINTING / SHIPPED / DONE / CANCELED
    private String note;
    private String trackingNo;        // SHIPPED 전이면 null
    private String cancelReason;
    private Boolean needsReview;
    private LocalDateTime requestedAt;
    private LocalDateTime updatedAt;
}
