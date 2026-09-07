package com.templestamp.user.dto;

import com.templestamp.global.type.Tier;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 내 정보 수정 요청을 전달받는 DTO Record입니다.
 * 모든 필드가 선택이며 null 은 "변경하지 않음"을 뜻하므로, 보낸 필드만 골라서 UPDATE 합니다.
 * (@NotBlank 를 쓰지 않는 이유 — null 을 허용해야 하기 때문. 값이 왔을 때만 @Size 가 검사됨)
 * [사용 위치] UserController — @RequestBody @Valid
 */
public record UserUpdateRequest(

        @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하로 입력해주세요.")
        String nickname,

        Tier tier,                      // 잘못된 값은 Jackson 이 400 으로 차단

        @Pattern(regexp = "^(ko|en|ja|zh)$", message = "지원하지 않는 언어입니다.")
        String locale,

        Boolean notificationEnabled
) {
}
