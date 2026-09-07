package com.templestamp.reward.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 실물 보상 수령 신청. 받는 사람·연락처·주소를 여기서 받는다.
 * <p>
 * 이 값들은 {@code reward_claim} 표에만 들어가고 보상 목록 응답에는 실리지 않는다 —
 * 목록은 화면·로그·캐시를 타는 흔한 경로라, 거기 주소가 섞이면 되돌릴 수 없다(챕터 7 보강 B-4).
 * [사용 위치] RewardController — @RequestBody @Valid
 */
public record ClaimRequest(

        @NotBlank(message = "받는 분 이름을 입력해주세요.")
        @Size(max = 50, message = "이름은 50자 이하로 입력해주세요.")
        String recipientName,

        @NotBlank(message = "연락처를 입력해주세요.")
        @Size(max = 30, message = "연락처는 30자 이하로 입력해주세요.")
        // 숫자·하이픈·괄호·공백·국가번호만. 형식을 더 좁히면 해외 번호가 막힌다.
        @Pattern(regexp = "^[0-9+(). -]{7,30}$", message = "연락처 형식이 올바르지 않습니다.")
        String phone,

        @NotBlank(message = "주소를 입력해주세요.")
        @Size(max = 200, message = "주소는 200자 이하로 입력해주세요.")
        String address,

        @Size(max = 200, message = "메모는 200자 이하로 입력해주세요.")
        String memo
) {
}
