// src/main/java/com/templestamp/kakao/KakaoProperties.java
package com.templestamp.kakao;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * kakao.* 바인딩. REST 키는 .env.local 의 KAKAO_REST_API_KEY 에서 온다 — 커밋되는 .env 에는 절대 넣지 않는다.
 * 키가 비어 있어도 기동은 막지 않는다(RequiredEnvCheck 대상 아님): 관리자 검색만 503 이고 나머지 서비스는 정상이어야 하므로.
 * TempleStampApplication 의 @ConfigurationPropertiesScan 이 읽는다.
 */
@ConfigurationProperties(prefix = "kakao")
public record KakaoProperties(
        String restKey,          // kakao.rest-key ← ${KAKAO_REST_API_KEY:}  (저장소 기존 키 이름 유지. 콜론 뒤가 비어 있어 미설정이면 "" 로 바인딩)
        String baseUrl,          // kakao.base-url = https://dapi.kakao.com  (테스트에서 MockWebServer 주소로 바꿔 끼우기 위해 설정으로 뺌)
        int timeoutMillis        // kakao.timeout-millis. 교재 [기본 35] 에는 없으나 남긴다 — 아래 KakaoMapConfig 주석 참고
) {
    /** 키가 있는가. 공백만 있는 것도 없는 것으로 본다 */
    public boolean hasKey() { return restKey != null && !restKey.isBlank(); }
}
