package com.templestamp.kakao.dto;

import java.math.BigDecimal;

/**
 * 카카오 검색 결과를 우리 규격으로 변환해 내려주는 DTO Record입니다.
 * x·y 를 longitude·latitude 로 바꿔 site 테이블 컬럼명과 맞추는 것이 핵심 역할입니다.
 * [사용 위치] KakaoMapService 가 KakaoPlaceDocument → 변환 (관리자 사찰 등록 화면용)
 */
public record KakaoPlaceResponse(
        String placeName,
        BigDecimal latitude,        // 카카오 y
        BigDecimal longitude,       // 카카오 x
        String addressName,
        String roadAddressName,
        String phone,
        String categoryName,
        String placeUrl
) {
    public static KakaoPlaceResponse from(KakaoPlaceDocument doc) {
        return new KakaoPlaceResponse(
                doc.placeName(),
                toDecimal(doc.y()),
                toDecimal(doc.x()),
                doc.addressName(),
                doc.roadAddressName(),
                doc.phone(),
                doc.categoryName(),
                doc.placeUrl());
    }

    /** 카카오가 좌표를 빈 문자열로 주는 문서가 드물게 있다. 거기서 터지지 않게 감싼다. */
    private static BigDecimal toDecimal(String value) {
        try {
            return value == null || value.isBlank() ? null : new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
