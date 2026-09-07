// src/main/java/com/templestamp/kakao/KakaoMapConfig.java
package com.templestamp.kakao;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 카카오 로컬 API 전용 RestClient 빈 하나. 인증 헤더 "Authorization: KakaoAK {키}" 는 여기서 한 번만 붙인다.
 * RestClient 는 Spring 6.1+ 의 동기 HTTP 클라이언트 — RestTemplate 후속. 빌더로 baseUrl·기본 헤더를 고정해 두면
 * Service 는 경로와 쿼리만 쓴다.
 * <p>
 * 타임아웃(기본 3초)은 교재 [기본 35] 에 없지만 남겨 둔다. 장소 검색은 관리자 화면의 부가 기능인데,
 * 타임아웃이 없으면 카카오가 느려질 때 우리 톰캣 스레드가 그만큼 묶이고 정작 중요한 순례 요청이 밀린다.
 * 외부 호출에 상한을 두지 않는 것은 그 자체로 장애 경로다.
 */
@Configuration
@RequiredArgsConstructor
public class KakaoMapConfig {

    private final KakaoProperties props;

    @Bean
    public RestClient kakaoRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(props.timeoutMillis()));
        factory.setReadTimeout(Duration.ofMillis(props.timeoutMillis()));

        return RestClient.builder()
                .baseUrl(props.baseUrl())                                       // https://dapi.kakao.com
                .requestFactory(factory)
                .defaultHeader("Authorization", "KakaoAK " + props.restKey())   // 키가 "" 여도 빈은 만든다 — 호출 시 Service 가 먼저 막는다
                .build();
    }
}
