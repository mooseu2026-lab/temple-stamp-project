package com.templestamp.photo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사진. 회원당 사찰당 한 장이다(uk_photo_user_site). 같은 사찰에서 다시 올리면 교체된다.
 * 실제 바이트는 오브젝트 스토리지에 있고 여기에는 키만 남는다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Photo {

    private Long photoId;
    private Long userId;
    private Long siteId;
    private String fileKey;
    private boolean hasOtherFace;
    private boolean isPrivate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 전자책에 실을 수 있는가. 타인 얼굴이 있거나 비공개면 싣지 않는다. */
    public boolean isPublishable() {
        return !hasOtherFace && !isPrivate;
    }
}
