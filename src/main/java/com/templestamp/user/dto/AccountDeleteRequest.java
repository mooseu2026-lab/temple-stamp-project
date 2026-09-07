package com.templestamp.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 탈퇴 요청. 비밀번호를 다시 받는 이유는 되돌릴 수 없는 일이기 때문이다 —
 * 자리를 비운 사이 남이 눌러도 안 되고, 세션이 살아 있다는 것만으로 근거가 되지도 않는다.
 * [사용 위치] UserController — @RequestBody @Valid
 */
public record AccountDeleteRequest(

        @NotBlank(message = "비밀번호를 입력해주세요.")
        String password
) {
}
