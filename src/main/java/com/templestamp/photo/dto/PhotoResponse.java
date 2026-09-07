// src/main/java/com/templestamp/photo/dto/PhotoResponse.java
package com.templestamp.photo.dto;

import java.time.LocalDateTime;

/**
 * 사찰 한 곳의 개인 소장 기록(사진 1장 + 문장 1개).
 * {@code photoKey} 는 저장소 키다 — 화면에 걸려면 별도 조회 URL 이 필요하다.
 */
public record PhotoResponse(
        Long siteId,
        String siteName,
        String photoKey,
        String sentence,            // 문장이 아직 없으면 null (사진만 있는 옛 행)
        boolean hasOtherFace,
        boolean isPrivate,
        LocalDateTime updatedAt
) {
    public static PhotoResponse from(PhotoRow r) {
        return new PhotoResponse(r.getSiteId(), r.getSiteName(), r.getPhotoKey(), r.getSentence(),
                Boolean.TRUE.equals(r.getHasOtherFace()), Boolean.TRUE.equals(r.getIsPrivate()),
                r.getUpdatedAt());
    }
}
