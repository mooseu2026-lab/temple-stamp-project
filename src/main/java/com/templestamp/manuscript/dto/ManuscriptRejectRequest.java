package com.templestamp.manuscript.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 반려. 사유는 <b>필수</b>다 — 고쳐서 다시 내야 하는 사람에게 무엇을 고칠지 알려 주지 않으면
 * 같은 원고가 그대로 다시 올라온다.
 * [사용 위치] AdminManuscriptController — @RequestBody @Valid
 */
public record ManuscriptRejectRequest(

        @NotBlank(message = "반려 사유를 입력해주세요.")
        @Size(min = 1, max = 200, message = "반려 사유는 1~200자로 입력해주세요.")
        String reason
) {
}
