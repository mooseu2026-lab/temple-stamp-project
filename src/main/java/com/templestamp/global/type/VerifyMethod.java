package com.templestamp.global.type;

/**
 * 스탬프 인증 방식.
 * GPS_QR   : 현장 위치 확인 → QR 스캔 → 미션 제출, 정상 3단계
 * EVIDENCE : GPS 가 잡히지 않거나 정확도가 낮을 때의 예외 접수. 관리자 심사를 거친다
 */
public enum VerifyMethod {
    GPS_QR,
    EVIDENCE
}
