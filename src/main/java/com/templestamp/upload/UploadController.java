package com.templestamp.upload;

import com.templestamp.global.response.ApiResponse;
import com.templestamp.global.security.AuthenticatedUser;
import com.templestamp.global.type.UploadPurpose;
import com.templestamp.upload.dto.PresignResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    /**
     * presign 이 내준 URL 로 바이트를 받는다(챕터 11-A · provider=local 일 때만 쓰인다).
     * <p>
     * 프론트 계약은 바뀌지 않는다 — presign 응답의 {@code uploadUrl} 로 PUT 하는 것은 전과 같고,
     * 그 주소가 가리키는 곳만 우리 앱이 되었다. S3 로 바꿀 때는 {@code storage.endpoint} 만
     * S3 주소로 되돌리면 이 문은 쓰이지 않게 되고 프론트 코드는 그대로다.
     * <p>
     * 경로에 {@code /**} 를 쓰는 이유는 fileKey 가 {@code PHOTO/17/uuid.jpg} 처럼 슬래시를
     * 품고 있어서다. {@code @PathVariable} 하나로는 첫 토막만 잡힌다.
     */
    @PutMapping("/**")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> receive(@AuthenticationPrincipal AuthenticatedUser user,
                                     HttpServletRequest request,
                                     @RequestParam long expires,
                                     @RequestParam String signature,
                                     @RequestHeader(value = "Content-Type", required = false) String contentType,
                                     @RequestBody byte[] bytes) {
        // presign 이 만드는 주소는 {endpoint}/{bucket}/{fileKey} 다 — S3 와 같은 모양이다.
        // 그래서 우리 문도 버킷 토막을 <b>지나서</b> fileKey 를 읽는다. 벗기지 않으면
        // 첫 토막이 "temple-stamp-local" 이 되어 용도(PHOTO/EVIDENCE)를 못 읽는다.
        String path = request.getRequestURI();
        String prefix = request.getContextPath() + "/api/uploads/";
        String rest = path.startsWith(prefix) ? path.substring(prefix.length()) : "";
        String fileKey = uploadService.stripBucket(rest);
        uploadService.receive(user.userId(), fileKey, expires, signature, contentType, bytes);
        return ApiResponse.ok();
    }
}
