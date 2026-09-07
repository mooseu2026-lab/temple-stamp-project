package com.templestamp.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;  // yml → 객체 바인딩 어노테이션

/**
 * app.* 설정 바인딩 — 로그인 서명키(jwt)와 QR 서명키(qr)를 분리 보관.
 * TempleStampApplication 의 @ConfigurationPropertiesScan 이 읽는다.
 * 확정값: 액세스 1800초(30분), 리프레시 7일.
 * <p>
 * 이 상자에는 <b>비밀키와 유효시간</b>만 담는다. 스탬프 세션·보상 감사·스토리지·CORS 처럼
 * 성격이 다른 설정은 각자 상자(StampProperties·RewardProperties·StorageProperties·CorsProperties)를
 * 쓴다. 한 상자에 다 넣으면 "여기 뭐가 들었더라" 를 매번 확인해야 한다.
 */
@ConfigurationProperties(prefix = "app")                        // yml 의 app.* 을 이 클래스에 담는다
                                                                // ※ 이것만으로는 빈이 안 됨.
                                                                //   메인 클래스의 @ConfigurationPropertiesScan 이 스캔해야 함
public record AppProperties(Jwt jwt, Qr qr, Cookie cookie, String frontendUrl) {    // record = 불변 데이터 클래스.
                                                                // 괄호 안이 필드이자 생성자 파라미터.
                                                                // 접근자는 getJwt() 가 아니라 jwt() (record 규칙)

    public record Jwt(
            String secret,          // app.jwt.secret ← ${JWT_SECRET} ← .env 의 Base64 문자열
            long accessSeconds,     // app.jwt.access-seconds = 1800
                                    // ※ 하이픈이 카멜로 자동 변환됨(relaxed binding)
                                    // ※ 이름이 안 맞으면 에러 없이 0 이 들어간다 — 사고 지점
            int refreshDays         // app.jwt.refresh-days = 14. 쿠키 Max-Age 와 refresh_token.expires_at 이 같이 쓴다
    ) {}

    /**
     * QR 이미지에 굽는 주소의 앞부분(app.frontend-url). 예 {@code http://localhost:5173}.
     * <p>
     * 이 값은 <b>종이에 인쇄된다.</b> 운영에서 틀린 주소로 QR 을 찍으면 되돌리는 길이
     * "사찰마다 다시 붙이러 간다" 뿐이다. prod 는 기본값 없이 환경변수를 요구하고,
     * {@code RequiredEnvCheck} 가 없으면 기동을 멈춘다.
     */
    public record Qr(
            String secret,          // app.qr.secret. 로그인 키가 털려도 QR 위조는 못 하게 분리
            long tokenSeconds       // app.qr.token-seconds. 현장 QR 기본 유효 시간(초).
                                    // 상설 안내판은 발급 시 개별로 길게 줄 수 있다
    ) {}

    public record Cookie(
            boolean secure          // local=false(http) / prod=true(https)
                                    // ※ Boolean(객체) 아닌 boolean(primitive):
                                    //   설정 누락 시 null 이 아니라 false 로 안전하게 떨어짐.
                                    //   뒤집어 말하면 prod 에서 true 를 빠뜨리면
                                    //   에러 없이 Secure 없는 쿠키가 나감 → 배포 체크리스트 항목
    ) {}
}
