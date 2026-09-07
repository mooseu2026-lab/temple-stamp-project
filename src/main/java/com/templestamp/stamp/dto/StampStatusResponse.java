package com.templestamp.stamp.dto;

import com.templestamp.manuscript.dto.ExtPhraseResponse;
import com.templestamp.manuscript.dto.ManuscriptTextResponse;

import java.time.LocalDateTime;

/**
 * 스탬프 한 건의 현재 상태와 각 단계 통과 시각을 내려주는 DTO Record입니다.
 * [사용 위치] StampService 조립 → Controller 반환. 상태는 String(StampStatus.name())
 */
public record StampStatusResponse(
        Long stampId,
        Long courseSiteId,
        String status,                    // GPS_DONE / QR_DONE / COMPLETED / EXPIRED / PENDING / REJECTED
        LocalDateTime gpsVerifiedAt,      // 1단계 통과 시각
        LocalDateTime qrVerifiedAt,       // 2단계 통과 시각 — 미통과면 null
        LocalDateTime completedAt,        // 3단계 확정 시각 — 미확정이면 null
        LocalDateTime expiresAt,          // gps_verified_at + 60분 (계산값)

        // ── v4 · Q1 ① : 프론트가 "이미 발행된 도장" 을 그대로 보여줄 수 있게 하는 4필드 ──
        Long siteId,                      // 실제로 인증한 후보 사찰. 대표와 다를 수 있다
        String siteName,                  // siteId 가 null 이면 null
        String photoKey,                  // 그때 올린 사진. 없으면 null
        String userSentence,              // 그때 쓴 다짐. 없으면 null

        // ── 챕터 8 §3-1 : 이 세션에 못 박은 미션 원고. 세션이 사는 동안 바뀌지 않는다 ──
        ManuscriptTextResponse missionManuscript,

        // ── 챕터 8 §3-2 : 발행 시점에 못 박은 확장문구. 없으면 통째로 null(필드는 항상 있다) ──
        ExtPhraseResponse extPhrase
) {
}
