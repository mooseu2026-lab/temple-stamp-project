package com.templestamp.kakao.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 카카오 API 응답 전체(메타 + 문서 목록)를 그대로 받는 DTO Record입니다.
 * [사용 위치] KakaoMapService 의 역직렬화 대상
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoSearchRawResponse(
        @JsonProperty("meta") Meta meta,
        @JsonProperty("documents") List<KakaoPlaceDocument> documents
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(
            @JsonProperty("total_count") int totalCount,
            @JsonProperty("pageable_count") int pageableCount,
            @JsonProperty("is_end") boolean isEnd
    ) {}
}
