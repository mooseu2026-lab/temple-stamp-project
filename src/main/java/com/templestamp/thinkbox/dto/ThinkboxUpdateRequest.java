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

        /**
          * 비공개 여부. <b>DB 컬럼과 같은 뜻·같은 이름</b>이다.
          * 예전에는 {@code isPublic}(반대 뜻)이라 어디선가 한 번만 뒤집으면 조용히 반대로 저장됐다.
          * 전자책 공개 동의(EBOOK_PUBLIC) 확인은 Service 가 한다 — 공개로 바꿀 때만.
          */
        Boolean isPrivate
) {
}
