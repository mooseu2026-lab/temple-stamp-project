package com.templestamp.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * manuscript.* — 원고 규칙(챕터 8).
 * <p>
 * 상한을 설정으로 둔 이유는 콘텐츠가 쌓이는 속도를 아직 모르기 때문이다. 3편은 "같은 자리에서
 * 사람마다 다른 글을 보여 주되, 편집자가 관리할 수 있는" 크기로 잡은 시작값이다.
 * 기본 원고(site_id NULL)는 이 값과 무관하게 구·종류마다 한 편이다 — 대체제가 여럿이면 기본이 아니다.
 */
@ConfigurationProperties(prefix = "manuscript")
public record ManuscriptProperties(

        /** (사찰, 구, 종류)당 퇴역하지 않은 변형의 최대 수. 기본 3. */
        int maxVariants,

        /** CSV 한 파일의 행 상한. */
        int importMaxRows,

        /** CSV 파일 크기 상한(바이트). */
        long importMaxBytes
) {
}
