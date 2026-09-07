package com.templestamp.pilgrimage;

import com.templestamp.global.response.ApiResponse;
import com.templestamp.global.response.ItemsResponse;
import com.templestamp.global.security.AuthenticatedUser;
import com.templestamp.pilgrimage.dto.PassportResponse;
import com.templestamp.pilgrimage.dto.PilgrimageResponse;
import com.templestamp.pilgrimage.dto.PilgrimageStartRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pilgrimages")
@RequiredArgsConstructor
public class PilgrimageController {

    private final PilgrimageService pilgrimageService;
    private final PassportService passportService;

    /**
     * 순례 시작. 멱등하다 — 이미 시작한 코스면 기존 기록을 그대로 돌려주고 created=false 로 알린다.
     * 연타가 오류인 상황이 아니어서 두 경우 모두 200 이다.
     */
    @PostMapping
    public ApiResponse<PilgrimageResponse> start(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @Valid @RequestBody PilgrimageStartRequest request) {
        return ApiResponse.ok(pilgrimageService.start(user.userId(), request.courseId()));
    }

    @GetMapping
    public ApiResponse<ItemsResponse<PilgrimageResponse>> getMine(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(ItemsResponse.of(pilgrimageService.getMine(user.userId())));
    }

    /** 여권 전체. 권역 → 코스 → 칸 순서로 접혀서 나간다. */
    @GetMapping("/passport")
    public ApiResponse<PassportResponse> getPassport(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(passportService.getPassport(user.userId()));
    }
}
