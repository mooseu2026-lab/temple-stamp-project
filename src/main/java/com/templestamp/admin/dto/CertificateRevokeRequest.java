package com.templestamp.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 인증서 회수 요청. 사유는 <b>필수</b>다 — 회수는 되돌릴 수 없고, 나중에
 * "왜 무효인가" 를 설명해야 하는 쪽은 사람이다. 빈 사유로 눌러진 회수는 설명이 없다.
 * [사용 위치] AdminCertificateController — @RequestBody @Valid
 */
public record CertificateRevokeRequest(

        @NotBlank(message = "회수 사유를 입력해주세요.")
        @Size(min = 1, max = 200, message = "회수 사유는 1~200자로 입력해주세요.")
        String reason
) {
}
