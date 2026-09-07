package com.templestamp.admin;

import com.templestamp.global.response.ApiResponse;
import com.templestamp.stamp.CompletionBatchService;
import com.templestamp.stamp.CompletionService;
import com.templestamp.stamp.dto.BatchRecountResponse;
import com.templestamp.stamp.dto.RecountResponse;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 완주 재집계. 도장 승인·반려가 어떤 이유로 연쇄를 못 태웠을 때 손으로 맞추는 길이다.
 * <p>
 * 몇 번을 돌려도 같은 결과가 나온다(멱등). 두 번째부터 {@code created:0 canceled:0} 이면
 * 데이터가 이미 있어야 할 모양이라는 뜻이다.
 */
@RestController
@RequestMapping("/api/admin/completions")
@RequiredArgsConstructor
@Validated
public class AdminCompletionController {

    private final CompletionService completionService;
    private final CompletionBatchService completionBatchService;

    /** 한 사람의 모든 코스를 다시 센다. */
    @PostMapping("/recount/{userId}")
    public ApiResponse<RecountResponse> recount(@PathVariable @Positive Long userId) {
        return ApiResponse.ok(completionService.recount(userId));
    }

    /** 전체. 사용자 한 명이 한 트랜잭션이라, 한 명이 실패해도 나머지는 반영된다. */
    @PostMapping("/recount")
    public ApiResponse<BatchRecountResponse> recountAll() {
        return ApiResponse.ok(completionBatchService.recountAll());
    }
}
