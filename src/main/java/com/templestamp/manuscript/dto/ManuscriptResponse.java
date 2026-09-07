package com.templestamp.manuscript.dto;

import java.time.LocalDateTime;

/**
 * 원고 한 편을 내려주는 DTO Record입니다. 편집자 목록·관리자 심사 목록이 같은 모양을 씁니다.
 * 본문까지 싣는 이유는 심사하는 사람이 목록에서 바로 읽어야 하기 때문입니다.
 * [사용 위치] ManuscriptService → EditorManuscriptController · AdminManuscriptController
 */
public record ManuscriptResponse(
        Long manuscriptId,
        Long siteId,                // 기본 원고면 null
        String siteName,            // 기본 원고면 null
        Integer verseNo,
        String kind,
        Integer variantNo,
        String status,
        String title,
        String body,
        Long authorId,
        String authorNickname,
        String rejectReason,        // 반려 이력. 고쳐서 다시 내도 남는다
        LocalDateTime reviewedAt,
        LocalDateTime retiredAt,
        LocalDateTime createdAt
) {
    public static ManuscriptResponse from(ManuscriptRow row) {
        return new ManuscriptResponse(row.getManuscriptId(), row.getSiteId(), row.getSiteName(),
                row.getVerseNo(), row.getKind(), row.getVariantNo(), row.getStatus(),
                row.getTitle(), row.getBody(), row.getAuthorId(), row.getAuthorNickname(),
                row.getRejectReason(), row.getReviewedAt(), row.getRetiredAt(), row.getCreatedAt());
    }
}
