package com.templestamp.meditation;

import com.templestamp.global.response.ApiResponse;
import com.templestamp.global.security.AuthenticatedUser;
import com.templestamp.meditation.dto.MeditationDetailResponse;
import com.templestamp.meditation.dto.MeditationListResponse;
import com.templestamp.meditation.dto.MeditationLogRequest;
import com.templestamp.meditation.dto.MeditationLogResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/meditations")
@RequiredArgsConstructor
public class MeditationController {

    private final MeditationService meditationService;

    @GetMapping
    public ApiResponse<MeditationListResponse> getList(
            @RequestParam(required = false) String category) {
        return ApiResponse.ok(meditationService.getList(category));
    }

    @GetMapping("/{meditationId}")
    public ApiResponse<MeditationDetailResponse> getDetail(
            @PathVariable Long meditationId,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ApiResponse.ok(meditationService.getDetail(meditationId, acceptLanguage));
    }

    /** 재생 기록. 로그인이 필요하다. */
    @PostMapping("/logs")
    public ApiResponse<MeditationLogResponse> log(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @Valid @RequestBody MeditationLogRequest request) {
        return ApiResponse.ok(meditationService.log(user.userId(), request));
    }
}
