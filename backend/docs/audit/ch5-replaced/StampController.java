package com.templestamp.stamp;

import com.templestamp.global.response.ApiResponse;
import com.templestamp.global.security.AuthenticatedUser;
import com.templestamp.stamp.dto.EvidenceRequest;
import com.templestamp.stamp.dto.EvidenceResponse;
import com.templestamp.stamp.dto.GpsCheckRequest;
import com.templestamp.stamp.dto.GpsCheckResponse;
import com.templestamp.stamp.dto.MissionResultResponse;
import com.templestamp.stamp.dto.MissionSubmitRequest;
import com.templestamp.stamp.dto.QrVerifyRequest;
import com.templestamp.stamp.dto.StampStatusResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 3단계. 대상 사찰은 경로 변수(courseSiteId)로 받고, 본문에는 좌표가 들어가지 않는다.
 */
@RestController
@RequestMapping("/api/stamps")
@RequiredArgsConstructor
public class StampController {

    private final StampService stampService;

    /** 1단계 — 단말이 판정한 반경 내 여부와 정확도 등급만 받는다. */
    @PostMapping("/{courseSiteId}/gps-check")
    public ApiResponse<GpsCheckResponse> gpsCheck(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @PathVariable Long courseSiteId,
                                                  @Valid @RequestBody GpsCheckRequest request) {
        return ApiResponse.ok(stampService.gpsCheck(user.userId(), courseSiteId, request));
    }

    /** 2단계 — 현장 QR. 통과하면 미션이 배정된다. */
    @PostMapping("/{stampId}/qr-verify")
    public ApiResponse<StampStatusResponse> verifyQr(@AuthenticationPrincipal AuthenticatedUser user,
                                                     @PathVariable Long stampId,
                                                     @Valid @RequestBody QrVerifyRequest request) {
        return ApiResponse.ok(stampService.verifyQr(user.userId(), stampId, request));
    }

    /** 3단계 — 다짐 한 문장. 이동시간이 모자라면 완료 대신 심사 보류로 간다. */
    @PostMapping("/{stampId}/mission")
    public ApiResponse<MissionResultResponse> submitMission(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long stampId,
            @Valid @RequestBody MissionSubmitRequest request) {
        return ApiResponse.ok(stampService.submitMission(user.userId(), stampId, request));
    }

    /** 예외 접수 — GPS·QR 이 불가능할 때. 바로 심사 대기로 들어간다. */
    @PostMapping("/evidence")
    public ApiResponse<EvidenceResponse> submitEvidence(@AuthenticationPrincipal AuthenticatedUser user,
                                                        @Valid @RequestBody EvidenceRequest request) {
        return ApiResponse.ok(stampService.submitEvidence(user.userId(), request));
    }

    @GetMapping("/{stampId}")
    public ApiResponse<StampStatusResponse> getStatus(@AuthenticationPrincipal AuthenticatedUser user,
                                                      @PathVariable Long stampId) {
        return ApiResponse.ok(stampService.getStatus(user.userId(), stampId));
    }
}
