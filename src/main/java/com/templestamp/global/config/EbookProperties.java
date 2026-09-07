package com.templestamp.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ebook.* — 전자책·인쇄·청소기의 숫자들(챕터 9).
 * <p>
 * 상한을 코드에 박지 않는 이유는 늘 같다 — <b>운영 중에 바뀔 값</b>이기 때문이다.
 * 하루 몇 권까지 만들게 할지, 몇 권을 남겨 둘지는 저장소 비용과 사용자 불만 사이의 선이고
 * 그 선은 서비스가 커지면 움직인다.
 *
 * @param maxReady        사용자당 남겨 두는 READY 권수. 넘으면 가장 오래된 것을 지운다(주문 걸린 책 제외)
 * @param dailyLimit      하루에 만들 수 있는 권수(READY 기준)
 * @param photoMaxPx      PDF 에 넣을 사진의 긴 변 상한. 원본 그대로 넣으면 60장에 수백 MB 가 된다
 * @param jpegQuality     사진 재압축 품질(0~1)
 * @param buildPerRun     청소기 한 번에 만드는 권수
 * @param orphanPerRun    청소기 한 번에 지우는 파일 키 수
 * @param orphanMaxRetry  이 횟수를 넘기면 FAILED 로 접고 사람에게 넘긴다
 * @param tokenPerRun     청소기 한 번에 지우는 리프레시 토큰 행 수
 * @param tokenGraceHours 만료 시각에서 이만큼 더 지난 것만 지운다. 폐기됐어도 <b>미만료면 남긴다</b>
 * @param printPrice      부수당 가격(원). 결제 연동은 범위 밖 — 지금은 "관리자 확인 = 입금 확인"
 * @param publicBaseUrl   인증서 QR 이 가리키는 진위 확인 주소의 앞부분
 */
@ConfigurationProperties(prefix = "ebook")
public record EbookProperties(
        int maxReady,
        int dailyLimit,
        int photoMaxPx,
        float jpegQuality,
        int buildPerRun,
        int orphanPerRun,
        int orphanMaxRetry,
        int tokenPerRun,
        int tokenGraceHours,
        int printPrice,
        String publicBaseUrl
) {
}
