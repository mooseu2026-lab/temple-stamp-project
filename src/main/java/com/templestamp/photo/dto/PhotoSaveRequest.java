// src/main/java/com/templestamp/photo/dto/PhotoSaveRequest.java
package com.templestamp.photo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PUT /api/photos/{siteId} 본문. <b>사찰당 사진 1장·문장 1개</b> — 다시 보내면 새 행이 아니라 교체다.
 * <p>
 * 사진 바이트는 이 요청에 담기지 않는다. 앱이 presign URL 로 저장소에 직접 올린 뒤
 * 그때 받은 키({@code photoKey})만 보낸다. 서버는 그 키가 <b>내 것인지</b>만 확인한다.
 * <p>
 * {@code sentence} 는 "왜 찍었는지 한 줄"(F-12 memo)이라 필수다. 사진만 있고 이유가 없으면
 * 나중에 전자책에 실을 때 그 장면이 무엇이었는지 아무도 모른다.
 */
public record PhotoSaveRequest(

        @NotBlank(message = "사진 키가 필요합니다.")
        @Pattern(regexp = "^PHOTO/\\d+/[0-9a-f-]{36}\\.(jpg|png)$",
                message = "사진 키 형식이 올바르지 않습니다.")
        String photoKey,

        @NotBlank(message = "한 줄 문장을 입력해주세요.")
        @Size(max = 300, message = "문장은 300자 이하로 입력해주세요.")   // 생각상자 상한과 같다
        String sentence,

        /** 타인 얼굴 포함(자기 신고). 전자책에서 뺀다. */
        boolean hasOtherFace,

        /** 비공개. 전자책에서 뺀다. */
        boolean isPrivate
) {
}
