package com.templestamp.global.type;

/**
 * 단말이 측정한 GPS 정확도 등급. LOW 면 서버가 인증을 거부합니다.
 * [사용 위치] GpsCheckRequest 필드 타입 — 잘못된 문자열은 Jackson 이 400 으로 차단
 */
public enum AccuracyGrade {
    /** 50m 이하 */
    HIGH,
    /** 50m 초과 150m 이하 */
    MID,
    /** 150m 초과 — 재측위 요구 */
    LOW;

    /** 이 등급으로 인증을 통과시킬 수 있는가. LOW 는 stamp 에 저장조차 되지 않는다. */
    public boolean isAcceptable() {
        return this != LOW;
    }
}
