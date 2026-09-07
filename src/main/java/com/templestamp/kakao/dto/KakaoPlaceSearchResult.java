package com.templestamp.kakao.dto;

import java.util.List;

/**
 * 변환된 장소 목록과 검색 메타 정보를 담아 관리자 화면에 내려주는 DTO Record입니다.
 * [사용 위치] KakaoMapService 조립 → 관리자 Controller 반환
 */
public record KakaoPlaceSearchResult(
        List<KakaoPlaceResponse> places,
        int totalCount,
        boolean end                 // 카카오 is_end — 더 가져올 페이지가 없는지
) {
    public static KakaoPlaceSearchResult of(List<KakaoPlaceResponse> places,
                                            KakaoSearchRawResponse.Meta meta) {
        return new KakaoPlaceSearchResult(places,
                meta == null ? places.size() : meta.totalCount(),
                meta != null && meta.isEnd());
    }
}
