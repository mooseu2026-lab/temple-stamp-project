package com.templestamp.global.type;

/**
 * 스탬프 진행 상태.
 *
 * <pre>
 *  GPS_DONE → QR_DONE → COMPLETED        (GPS_QR 경로. GPS 통과 후 60분 안에 끝내야 한다)
 *  PENDING  → COMPLETED | REJECTED       (증빙 접수 / 이동시간 미달, 관리자 심사)
 *  GPS_DONE · QR_DONE 이 60분을 넘기면 EXPIRED
 * </pre>
 */
public enum StampStatus {

    GPS_DONE,
    QR_DONE,
    COMPLETED,
    EXPIRED,
    PENDING,
    REJECTED;

    public boolean isFinal() {
        return this == COMPLETED || this == REJECTED || this == EXPIRED;
    }

    public boolean isInProgress() {
        return this == GPS_DONE || this == QR_DONE;
    }

    /** 이 상태의 행 위에 GPS 인증을 다시 걸 수 있는가. COMPLETED·PENDING 은 건드리지 않는다. */
    public boolean isRestartable() {
        return this == GPS_DONE || this == QR_DONE || this == EXPIRED || this == REJECTED;
    }
}
