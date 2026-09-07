package com.templestamp.global.type;

/**
 * 사용자 타겟 계층 enum. 확장문구와 미션을 어느 풀에서 뽑을지 결정하는 기준값입니다.
 * users.tier 가 NULL 이면 AGE30 으로 취급(JWT에 넣지 않음).
 * [사용 위치] UserUpdateRequest 필드 타입, SitePageService 의 문구·미션 선택
 */
public enum Tier {
    AGE20, AGE30, AGE40, AGE50, AGE60, RIDER, FOREIGN;

    /** users.tier 는 문자열이자 NULL 허용이라, 문자열을 받아 기본값까지 한 번에 처리한다. */
    public static Tier orDefault(String value) {
        return value == null || value.isBlank() ? AGE30 : valueOf(value);
    }

    public static Tier orDefault(Tier value) {
        return value == null ? AGE30 : value;
    }
}
