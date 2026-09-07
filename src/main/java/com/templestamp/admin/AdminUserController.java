package com.templestamp.admin;

import com.templestamp.admin.dto.AdminUserResponse;
import com.templestamp.global.response.ApiResponse;
import com.templestamp.global.response.PageResponse;
import com.templestamp.user.UserService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 사용자 목록(챕터 9 §6).
 * <p>
 * 기본이 <b>ACTIVE</b> 다. 탈퇴 계정은 {@code ?status=DELETED} 를 <b>명시할 때만</b> 보인다 —
 * 기본으로 섞이면 "탈퇴했는데 목록에 있다" 가 되고, 그러면 관리자가 탈퇴한 사람에게
 * 연락하거나 처리를 시도하는 일이 생긴다. 점검 S22 가 이것을 실측한다.
 */
@Validated
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    public ApiResponse<PageResponse<AdminUserResponse>> list(
            @RequestParam(defaultValue = "ACTIVE") String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(userService.listForAdmin(status, q, page, size));
    }
}
