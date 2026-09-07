package com.templestamp.ebook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 인쇄 주문의 배송정보(챕터 9 §4). 본체와 1:1 이고 <b>목록 질의는 이 표를 읽지 않는다</b>.
 * 따로 둔 이유는 하나다 — 같은 표에 있으면 목록을 부를 때마다 주소가 함께 나갈 길이 열린다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintOrderAddress {
    private Long printOrderId;
    private String recipient;
    private String phone;
    private String postalCode;
    private String address;
    private String memo;
}
