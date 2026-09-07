package com.templestamp.thinkbox;

import com.templestamp.global.response.ApiResponse;
import com.templestamp.global.response.PageResponse;
import com.templestamp.global.security.AuthenticatedUser;
import com.templestamp.thinkbox.dto.ThinkboxCreateRequest;
import com.templestamp.thinkbox.dto.ThinkboxResponse;
import com.templestamp.thinkbox.dto.ThinkboxUpdateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/thinkbox")
@RequiredArgsConstructor
public class ThinkboxController {

    private final ThinkboxService thinkboxService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Long> create(@AuthenticationPrincipal AuthenticatedUser user,
                                    @Valid @RequestBody ThinkboxCreateRequest request) {
        return ApiResponse.ok(thinkboxService.create(user.userId(), request));
    }

    @GetMapping
    public ApiResponse<PageResponse<ThinkboxResponse>> getMine(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(thinkboxService.getMine(user.userId(), page, size));
    }

    @PatchMapping("/{thinkboxId}")
    public ApiResponse<Void> update(@AuthenticationPrincipal AuthenticatedUser user,
                                    @PathVariable Long thinkboxId,
                                    @Valid @RequestBody ThinkboxUpdateRequest request) {
        thinkboxService.update(user.userId(), thinkboxId, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{thinkboxId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser user,
                                    @PathVariable Long thinkboxId) {
        thinkboxService.delete(user.userId(), thinkboxId);
        return ApiResponse.ok();
    }
}
