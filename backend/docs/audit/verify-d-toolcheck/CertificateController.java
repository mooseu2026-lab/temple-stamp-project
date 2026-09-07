package com.templestamp.certificate;

import com.templestamp.certificate.dto.CertificateResponse;
import com.templestamp.certificate.dto.CertificateVerifyResponse;
import com.templestamp.global.response.ApiResponse;
import com.templestamp.global.response.ItemsResponse;
import com.templestamp.global.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;

    @GetMapping
    public ApiResponse<ItemsResponse<CertificateResponse>> getMine(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(ItemsResponse.of(certificateService.getMyCertificates(user.userId())));
    }

    /** 공개 검증. 인증이 필요 없고 닉네임은 마스킹돼 나간다. */
    @GetMapping("/verify/{serialNo}")
    public ApiResponse<CertificateVerifyResponse> verify(@PathVariable String serialNo) {
        return ApiResponse.ok(certificateService.verify(serialNo));
    }
}
