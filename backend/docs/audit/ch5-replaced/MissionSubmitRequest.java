package com.templestamp.stamp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 인증 3단계에서 다짐 문장과 사진 키를 전달받는 DTO Record입니다.
 * 사진 키는 패턴 검증으로 남의 파일이나 경로 조작(../ 등)을 가리키지 못하게 막습니다.
 * 키 규칙: presign 이 최종 키 PHOTO/{userId}/{uuid}.jpg 를 바로 발급 — tmp/ 접두어 없음, 가공 없음.
 * 빈 문자열 허용(^$|) — 프론트가 "" 를 보냈다고 미션 제출이 실패하는 대가가 더 크기 때문.
 * 키 안의 {userId}가 본인인지는 Service 가 추가 확인.
 * [사용 위치] StampController — @RequestBody @Valid
 */
public record MissionSubmitRequest(

        @NotBlank(message = "다짐 문장을 입력해주세요.")
        @Size(min = 10, max = 300, message = "다짐 문장은 10자 이상 300자 이하로 입력해주세요.")
        String sentence,

        @Pattern(
                regexp = "^$|^PHOTO/\\d+/[0-9a-f-]{36}\\.jpg$",
                message = "사진 키 형식이 올바르지 않습니다."
        )
        String photoKey,       // 선택. null·"" 모두 사진 없음으로 처리

        /** 사진에 다른 사람 얼굴이 담겼는지 스스로 알려 주는 값. 전자책 수록에서 걸러 낸다. */
        Boolean hasOtherFace
) {
}
