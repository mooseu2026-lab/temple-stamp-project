package com.templestamp.upload;

import com.templestamp.global.response.ApiResponse;
import com.templestamp.global.security.AuthenticatedUser;
import com.templestamp.global.type.UploadPurpose;
import com.templestamp.upload.dto.PresignResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    /**
     * 업로드용 서명 URL 발급. 파일 자체는 서버를 거치지 않고 스토리지로 직접 올라간다.
     * purpose 가 enum 이라 PHOTO·EVIDENCE 외 값은 Jackson 이 400 으로 막는다.
     */
    @GetMapping("/presign")
    public ApiResponse<PresignResponse> presign(@AuthenticationPrincipal AuthenticatedUser user,
                                                @RequestParam UploadPurpose purpose,
                                                @RequestParam @NotBlank String contentType) {
        // purpose 가 PHOTO·EVIDENCE 가 아니면 Spring 이 먼저 400 COMMON-4003 으로 막는다(enum 변환 실패).
        return ApiResponse.ok(uploadService.presign(user.userId(), purpose, contentType));
    }
}
