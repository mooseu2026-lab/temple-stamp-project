package com.templestamp.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청 데이터를 전달받고 유효성을 검증하는 DTO Record입니다.
 * 형식 검증만 담당하고, 이메일 중복 확인은 Service 가 합니다.
 * [사용 위치] AuthController — @RequestBody @Valid
 */
public record SignupRequest(

        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 100, message = "이메일은 100자 이하로 입력해주세요.")
        String email,

        @NotBlank(message = "비밀번호를 입력해주세요.")
        @Size(min = 8, max = 50, message = "비밀번호는 8자 이상 50자 이하로 입력해주세요.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9\s]).{8,50}$",
                message = "비밀번호는 영문, 숫자, 특수문자를 각각 1자 이상 포함해야 합니다."
        )
        String password,

        @NotBlank(message = "닉네임을 입력해주세요.")
        @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하로 입력해주세요.")
        String nickname
) {
}
