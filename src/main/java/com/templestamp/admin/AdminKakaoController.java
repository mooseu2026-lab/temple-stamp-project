// src/main/java/com/templestamp/admin/AdminKakaoController.java
package com.templestamp.admin;

import com.templestamp.global.response.ApiResponse;
import com.templestamp.kakao.KakaoMapService;
import com.templestamp.kakao.dto.KakaoPlaceSearchResult;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/admin/kakao/places?query=통도사&page=1&size=15  — ADMIN 전용(SecurityConfig [admin] 구역).
 * 응답은 KakaoPlaceSearchResult{ places[], totalCount, end }. 관리자가 한 건을 골라 AdminSiteSaveRequest 의 latitude/longitude 에 넣는다.
 */
@RestController
@RequestMapping("/api/admin/kakao")
@RequiredArgsConstructor
@Validated
@Slf4j
public class AdminKakaoController {

    private final KakaoMapService kakaoMapService;

    @GetMapping("/places")
    public ApiResponse<KakaoPlaceSearchResult> places(
            @RequestParam @NotBlank @Size(max = 100) String query,          // 빈 문자열은 카카오도 400 을 주지만 우리가 먼저 COMMON-4000
            @RequestParam(defaultValue = "1")  @Min(1) @Max(45) int page,   // 카카오 상한
            @RequestParam(defaultValue = "15") @Min(1) @Max(15) int size) {
        log.debug("GET /api/admin/kakao/places query={} page={} size={}", query, page, size);
        return ApiResponse.ok(kakaoMapService.search(query, page, size));
    }
}
