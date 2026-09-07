package com.templestamp.kakao.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 카카오 장소 검색 API 가 돌려주는 장소 한 건을 그대로 받는 DTO Record입니다.
 * 스네이크 케이스 필드를 @JsonProperty 로 매핑합니다. 우리 응답으로 직접 나가지 않습니다.
 * [사용 위치] KakaoMapService(RestClient) 의 역직렬화 대상
 */
@JsonIgnoreProperties(ignoreUnknown = true)   // 카카오가 필드를 추가해도 깨지지 않게
public record KakaoPlaceDocument(
        @JsonProperty("id") String id,
        @JsonProperty("place_name") String placeName,
        @JsonProperty("category_name") String categoryName,
        @JsonProperty("phone") String phone,
        @JsonProperty("address_name") String addressName,
        @JsonProperty("road_address_name") String roadAddressName,
        @JsonProperty("x") String x,              // 경도 — 카카오는 문자열로 준다
        @JsonProperty("y") String y,              // 위도
        @JsonProperty("place_url") String placeUrl
) {
}
