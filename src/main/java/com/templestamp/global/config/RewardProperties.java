package com.templestamp.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * reward.* — 보상 감사 규칙.
 * <p>
 * auditBlocking 기본값이 false 인 것이 핵심이다. 초기엔 점수를 기록만 하고 전부 통과시킨다.
 * 임계치를 실제 데이터로 검증하기 전에 차단부터 켜면, 통신이 나쁜 곳에서 예외 접수를
 * 여러 번 쓴 정상 사용자가 먼저 잘린다.
 */
@ConfigurationProperties(prefix = "reward")
public record RewardProperties(
        boolean auditBlocking,
        int reviewThreshold,
        long minGapSeconds,

        /**
         * 회향(전 코스 완주)을 인정하기 시작하는 <b>최소 ACTIVE 코스 수</b>. 확정 12.
         * <p>
         * 이 하한이 없으면 "완주한 코스 수 ≥ 전체 ACTIVE 코스 수" 가 코스가 적을 때 너무 쉽게 참이 된다 —
         * ACTIVE 코스가 1개뿐인 초기에는 <b>코스 하나만 끝내도 회향 인증서와 실물 기념품이 나간다.</b>
         * 데이터가 덜 찼을 때는 회향이 안 나가는 쪽이 안전하다(정리.md §6-8).
         */
        int hoehyangMinCourses
) {
}
