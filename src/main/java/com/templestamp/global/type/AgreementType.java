package com.templestamp.global.type;

/**
 * user_agreement.agreement_type — 사용자가 동의한 약관의 종류.
 * DB CHECK 제약과 값이 반드시 일치해야 한다.
 * [사용 위치] AgreementRequest 필드 타입
 */
public enum AgreementType {
    /** 위치정보 이용 동의 (명세 16.2) */
    LOCATION_SERVICE,
    /** 전자책에 내 기록을 공개 수록하는 것에 대한 동의 (명세 15.3) */
    EBOOK_PUBLIC;

    /** 현재 유효한 약관 버전. 개정하면 이 값을 올린다. */
    public static final int CURRENT_VERSION = 1;
}
