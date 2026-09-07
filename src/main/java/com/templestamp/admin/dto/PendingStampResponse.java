package com.templestamp.admin.dto;

import java.time.LocalDateTime;

/**
 * 관리자 검토 목록의 한 건을 내려주는 DTO Record입니다.
 * photoUrl 은 저장소 키를 임시 조회 URL 로 바꾼 값 — 키 자체는 관리자에게도 안 내보냅니다.
 * [사용 위치] AdminStampController — Service 가 PendingStampRow + presign 조립
 */
public record PendingStampResponse(
        Long stampId,
        Long userId,
        String nickname,           // 관리자 화면이므로 마스킹 없음
        String courseName,
        String siteName,
        String verifyMethod,
        String pendingReason,
        String sentence,
        String photoUrl,           // 임시 URL. 사진 없으면 null
        LocalDateTime requestedAt
) {
    public static PendingStampResponse of(PendingStampRow row, String photoUrl) {
        return new PendingStampResponse(
                row.getStampId(), row.getUserId(), row.getNickname(),
                row.getCourseName(), row.getSiteName(), row.getVerifyMethod(),
                row.getPendingReason(), row.getUserSentence(), photoUrl, row.getCreatedAt());
    }
}
