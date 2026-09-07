package com.templestamp.reward;

import com.templestamp.global.response.ApiResponse;
import com.templestamp.global.response.ItemsResponse;
import com.templestamp.global.security.AuthenticatedUser;
import com.templestamp.reward.dto.ClaimRequest;
import com.templestamp.reward.dto.ClaimResponse;
import com.templestamp.reward.dto.RewardResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/rewards")
@RequiredArgsConstructor
public class RewardController {

    private final RewardService rewardService;

    @GetMapping
    public ApiResponse<ItemsResponse<RewardResponse>> getRewards(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(ItemsResponse.of(rewardService.getRewards(user.userId())));
    }

    /**
     * 실물 보상 수령 신청. 배송 정보를 함께 받는다 — 받는 사람·연락처·주소는 여기서만 오가고
     * 보상 목록 응답에는 실리지 않는다(챕터 7 보강 B-4).
     */
    @PostMapping("/{userRewardId}/claim")
    public ApiResponse<ClaimResponse> claim(@AuthenticationPrincipal AuthenticatedUser user,
                                            @PathVariable Long userRewardId,
                                            @Valid @RequestBody ClaimRequest request) {
        return ApiResponse.ok(rewardService.claim(user.userId(), userRewardId, request));
    }
}
