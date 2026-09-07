package com.templestamp.thinkbox.dto;

import jakarta.validation.constraints.Size;

/**
 * 기존 기록의 본문이나 공개 여부를 수정할 때 쓰는 DTO Record입니다.
 * 두 필드 모두 선택이며 null 은 변경 없음을 뜻합니다.
 * [사용 위치] ThinkboxController — @RequestBody @Valid
 */
public record ThinkboxUpdateRequest(

        @Size(min = 1, max = 300, message = "내용은 1자 이상 300자 이하로 입력해주세요.")
        String content,

        Boolean isPublic       // 전자책 공개 수록 여부. EBOOK_PUBLIC 동의 확인은 Service
) {
}
