package com.templestamp.global.type;

/**
 * 단말이 측정한 GPS 정확도 등급. LOW 면 서버가 인증을 거부합니다.
 * <p>
 * <b>등급을 매기는 쪽은 단말이다.</b> 서버는 좌표를 받지 않으므로 미터 값을 다시 잴 수 없고,
 * 여기 적힌 경계는 프론트({@code location.js})가 Geolocation 의 {@code coords.accuracy} 를
 * 접는 기준이자 이 이름들의 뜻이다. 두 곳이 어긋나면 같은 상황이 기기마다 다른 등급으로 올라온다 —
 * 그래서 이 주석과 {@code frontend-handoff.md} §정확도 가 한 쌍이다(챕터 11 결정 F).
 * <p>
 * 경계는 인계문 기준이며 현장 실측 뒤 조정한다. 바꾸면 <b>두 곳을 함께</b> 바꾼다.
 * [사용 위치] GpsCheckRequest 필드 타입 — 잘못된 문자열은 Jackson 이 400 으로 차단
 */
public enum AccuracyGrade {
    /** accuracy ≤ 30m */
    HIGH,
    /** 30m &lt; accuracy ≤ 100m */
    MID,
    /** 100m 초과 — 서버를 부르지 않고 재측위를 안내한다(E-02) */
    LOW;

    /** 이 등급으로 인증을 통과시킬 수 있는가. LOW 는 stamp 에 저장조차 되지 않는다. */
    public boolean isAcceptable() {
        return this != LOW;
    }
}
