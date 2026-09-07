// src/main/java/com/templestamp/kakao/KakaoMapService.java
package com.templestamp.kakao;

import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.kakao.dto.KakaoPlaceResponse;
import com.templestamp.kakao.dto.KakaoPlaceSearchResult;
import com.templestamp.kakao.dto.KakaoSearchRawResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 카카오 로컬 "키워드로 장소 검색" — GET /v2/local/search/keyword.json?query=&page=&size=
 *   page 1~45, size 1~15 (카카오 제한). 범위는 컨트롤러 @Min/@Max 가 먼저 막는다.
 * DB 를 건드리지 않으므로 @Transactional 없음. 결과는 저장하지 않는다 — 관리자가 고른 것만 site 로 들어간다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KakaoMapService {

    private final KakaoProperties props;
    private final RestClient kakaoRestClient;                  // KakaoMapConfig 의 빈. 이름으로 주입된다

    public KakaoPlaceSearchResult search(String query, int page, int size) {
        if (!props.hasKey()) {
            // 키가 없는 것은 "카카오가 잘못 응답한 것"(502) 이 아니라 "우리가 아직 준비 안 됨"(503).
            throw new BusinessException(ErrorCode.KAKAO_5030);
        }
        try {
            KakaoSearchRawResponse raw = kakaoRestClient.get()
                    .uri(b -> b.path("/v2/local/search/keyword.json")
                            .queryParam("query", query)             // RestClient 가 URL 인코딩 — 한글 그대로 넘긴다
                            .queryParam("page", page)
                            .queryParam("size", size)
                            .build())
                    .retrieve()
                    .body(KakaoSearchRawResponse.class);           // documents[] + meta 를 record 로 역직렬화 (@JsonProperty snake_case)

            if (raw == null || raw.documents() == null) {          // 200 인데 본문이 비는 경우 방어
                throw new BusinessException(ErrorCode.KAKAO_5031);
            }
            var places = raw.documents().stream()
                    .map(KakaoPlaceResponse::from)                  // x·y(String) → longitude·latitude(BigDecimal). site 컬럼명과 통일
                    .toList();
            log.debug("kakao search query={} page={} size={} total={}", query, page, size, raw.meta().totalCount());
            return KakaoPlaceSearchResult.of(places, raw.meta());
        } catch (RestClientException e) {
            // 4xx/5xx·타임아웃·역직렬화 실패 전부 여기. 키 값 자체는 절대 로그에 남기지 않는다 —
            // 카카오 4xx 본문에 키 일부가 섞여 나올 수 있어 예외 메시지를 통째로 찍지 않고 클래스 이름만 남긴다.
            log.warn("kakao search failed query={} cause={}", query, e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.KAKAO_5031);
        }
    }
}
