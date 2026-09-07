package com.templestamp.stamp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * GPS·QR 인증이 불가능한 상황에서 증거 사진과 문장을 전달받는 DTO Record입니다.
 * 접수되면 stamp 는 PENDING + verify_method=EVIDENCE 로 생성되고 관리자 승인을 기다립니다.
 * 증거 사진은 필수이며 EVIDENCE/ 키만 허용(빈 문자열 불가 — 미션 사진과 다름).
 * [사용 위치] StampController — @RequestBody @Valid
 */
public record EvidenceRequest(

        @NotNull(message = "대상 자리가 필요합니다.")
        @Positive(message = "자리 번호가 올바르지 않습니다.")
        Long courseSiteId,

        /**
         * v4 — 그 자리에서 실제로 서 있는 후보 사찰. GPS·QR 을 거치지 않는 경로라
         * 이 값이 없으면 승인 뒤에도 {@code stamp.site_id} 가 비어 이동시간 검사와 여권의 사찰 이름이 어긋난다.
         * 이 자리의 후보가 아니면 400 COURSE-4001.
         */
        @NotNull(message = "사찰이 필요합니다.")
        @Positive(message = "사찰 번호가 올바르지 않습니다.")
        Long siteId,

        @NotBlank(message = "상황 설명 문장을 입력해주세요.")
        @Size(min = 10, max = 300, message = "문장은 10자 이상 300자 이하로 입력해주세요.")
        String sentence,

        @NotBlank(message = "증거 사진이 필요합니다.")
        @Pattern(
                regexp = "^EVIDENCE/\\d+/[0-9a-f-]{36}\\.(jpg|png)$",
                message = "증거 사진 키 형식이 올바르지 않습니다."
        )
        String photoKey
) {
}
