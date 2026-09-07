package com.templestamp.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 로그인에 필요한 이메일과 비밀번호를 전달받는 DTO Record입니다.
 * 형식 검증을 최소한으로 두어(이메일 형식 검사도 안 함) 계정 존재 여부가
 * 검증 메시지로 새어 나가지 않게 합니다. 실패는 항상 같은 문구로 응답.
 * [사용 위치] AuthController — @RequestBody @Valid
 */
public record LoginRequest(

        @NotBlank(message = "이메일을 입력해주세요.")
        String email,

        @NotBlank(message = "비밀번호를 입력해주세요.")
        String password
) {
}
