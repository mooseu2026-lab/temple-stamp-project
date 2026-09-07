package com.templestamp.thinkbox.dto;

import java.time.LocalDateTime;

/**
 * 생각상자 기록 한 건을 내려주는 DTO Record입니다.
 * 미션에서 자동 기록된 것과 직접 작성한 것을 source 로 구분합니다.
 * [사용 위치] ThinkboxService 가 ThinkboxRow → 변환, PageResponse 에 담김
 */
public record ThinkboxResponse(
        Long thinkboxId,
        String content,
        String source,
        Boolean isPublic,
        Boolean isEdited,
        Long courseId,
        String courseName,
        Long siteId,
        String siteName,
        LocalDateTime createdAt
) {
    public static ThinkboxResponse from(ThinkboxRow row) {
        return new ThinkboxResponse(row.getThinkboxId(), row.getContent(), row.getSource(),
                row.getIsPublic(), row.getIsEdited(), row.getCourseId(), row.getCourseName(),
                row.getSiteId(), row.getSiteName(), row.getCreatedAt());
    }
}
