package com.templestamp.admin;

import com.templestamp.global.housekeeping.HousekeepingScheduler;
import com.templestamp.global.housekeeping.dto.HousekeepingResult;
import com.templestamp.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 청소기 수동 실행(챕터 9 §5).
 * <p>
 * 5분을 기다리는 것 말고 다른 길이 있어야 한다 — 검증 세트가 "전자책이 만들어졌는가" 를
 * 확인하려면 그 사이 5분을 잘 수는 없고, 운영에서 큐가 밀렸을 때도 사람이 한 번 밀어 볼 수 있어야 한다.
 * 응답은 로그 한 줄과 <b>같은 숫자</b>다.
 */
@Validated
@RestController
@RequestMapping("/api/admin/housekeeping")
@RequiredArgsConstructor
public class AdminHousekeepingController {

    private final HousekeepingScheduler housekeepingScheduler;

    @PostMapping("/run")
    public ApiResponse<HousekeepingResult> run() {
        return ApiResponse.ok(housekeepingScheduler.run());
    }
}
