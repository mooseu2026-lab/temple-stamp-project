package com.templestamp.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 사찰 등록·수정 요청 DTO Record입니다. (수정 시 siteId 는 URL 경로 변수)
 * 좌표는 카카오 장소 검색(KakaoPlaceResponse)에서 골라 채우는 것이 기본 흐름입니다.
 * [사용 위치] AdminSiteController — @RequestBody @Valid. i18n 은 site_i18n 행으로 저장
 */
public record AdminSiteSaveRequest(

        @NotNull(message = "위도가 필요합니다.")
        @DecimalMin(value = "33.0", message = "위도 범위를 벗어났습니다.")     // [가정] 한국 영역으로 제한
        @DecimalMax(value = "39.0", message = "위도 범위를 벗어났습니다.")
        BigDecimal latitude,

        @NotNull(message = "경도가 필요합니다.")
        @DecimalMin(value = "124.0", message = "경도 범위를 벗어났습니다.")
        @DecimalMax(value = "132.0", message = "경도 범위를 벗어났습니다.")
        BigDecimal longitude,

        @NotNull(message = "인증 반경이 필요합니다.")
        @Min(value = 30, message = "인증 반경은 30m 이상이어야 합니다.")       // [가정] 최소·최대 반경
        @Max(value = 1000, message = "인증 반경은 1000m 이하여야 합니다.")
        Integer verifyRadius,

        @Size(max = 200, message = "QR 위치 안내는 200자 이하로 입력해주세요.")
        String qrLocationHint,

        @Size(max = 300, message = "주차 안내는 300자 이하로 입력해주세요.")
        String parkingInfo,

        @Size(max = 300, message = "진입 안내는 300자 이하로 입력해주세요.")
        String accessInfo,

        @Size(max = 20, message = "식사 안내는 20자 이하로 입력해주세요.")
        String mealAvailable,

        // status 는 받지 않는다. 등록·수정은 내용만 바꾸고, 공개 여부는 PATCH /api/admin/sites/{id}/status 만 바꾼다.
        // 한 요청에서 둘을 같이 하면 ACTIVE 전환 조건(qr 힌트·ko 행) 검사를 건너뛰고 공개되는 경로가 생긴다.

        @NotEmpty(message = "언어별 정보가 최소 1건(ko) 필요합니다.")
        List<@Valid I18nBlock> i18n
) {

    public record I18nBlock(

            @NotBlank(message = "언어 코드가 필요합니다.")
            @Pattern(regexp = "^(ko|en|ja|zh)$", message = "지원하지 않는 언어입니다.")
            String locale,

            @NotBlank(message = "사찰 이름을 입력해주세요.")
            @Size(max = 100, message = "사찰 이름은 100자 이하로 입력해주세요.")
            String name,

            @Size(max = 1000, message = "설명은 1000자 이하로 입력해주세요.")
            String description
    ) {}

    /** site.name 은 ko 이름을 쓴다. ko 가 없으면 목록의 첫 번째를 쓴다. */
    public String primaryName() {
        return i18n.stream()
                .filter(block -> "ko".equals(block.locale()))
                .findFirst()
                .orElse(i18n.get(0))
                .name();
    }
}
