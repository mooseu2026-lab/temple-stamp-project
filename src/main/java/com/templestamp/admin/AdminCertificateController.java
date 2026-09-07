package com.templestamp.admin;

import com.templestamp.admin.dto.CertificateRevokeRequest;
import com.templestamp.certificate.CertificateService;
import com.templestamp.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증서 회수. 발행은 완주가 하고, 회수는 완주 취소 연쇄가 한다 —
 * 여기는 그 둘로 설명되지 않는 경우(부정 발급이 뒤늦게 드러난 때)를 위한 문이다.
 * 지우지 않고 무효로 표시하므로, 회수된 번호도 공개 진위 확인에서 계속 조회된다.
 */
@RestController
@RequestMapping("/api/admin/certificates")
@RequiredArgsConstructor
@Validated
public class AdminCertificateController {

    private final CertificateService certificateService;

    @PostMapping("/{certificateId}/revoke")
    public ApiResponse<Void> revoke(@PathVariable @Positive Long certificateId,
                                    @Valid @RequestBody CertificateRevokeRequest request) {
        certificateService.revokeByAdmin(certificateId, request.reason());
        return ApiResponse.ok();
    }
}
