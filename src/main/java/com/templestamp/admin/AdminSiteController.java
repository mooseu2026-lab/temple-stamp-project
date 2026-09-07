package com.templestamp.admin;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.templestamp.admin.dto.AdminSiteListResponse;
import com.templestamp.admin.dto.AdminSiteSaveRequest;
import com.templestamp.admin.dto.SiteBadgeSaveRequest;
import com.templestamp.admin.dto.SiteViewpointSaveRequest;
import com.templestamp.admin.dto.StatusChangeRequest;
import com.templestamp.admin.dto.QrIssueResponse;
import com.templestamp.course.CourseService;
import com.templestamp.global.config.AppProperties;
import com.templestamp.course.dto.CourseSiteRow;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.global.response.ApiResponse;
import com.templestamp.global.response.PageResponse;
import com.templestamp.site.Site;
import com.templestamp.site.SiteService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import com.templestamp.site.dto.SiteResponse;
import com.templestamp.stamp.QrTokenProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Map;

/**
 * 사찰 관리와 QR 발급.
 * 좌표를 본문으로 받는 유일한 경로라 CoordinateFieldGuard 화이트리스트에 올라가 있다 —
 * 사찰 좌표는 시설의 고정 위치이며 개인 위치정보가 아니다.
 */
@Validated
@RestController
@RequestMapping("/api/admin/sites")
@RequiredArgsConstructor
public class AdminSiteController {

    private static final int QR_SIZE_PX = 512;

    private final SiteService siteService;
    private final CourseService courseService;
    private final QrTokenProvider qrTokenProvider;
    private final AdminSiteService adminSiteService;
    private final AppProperties appProperties;   // QR 에 구울 프론트 주소

    /**
     * 관리자 목록·검색. 옛 공개 {@code GET /api/sites} 를 여기로 옮겼다 —
     * 사용자 흐름은 권역→코스→사찰이라 전체 검색이 없고, 필요한 곳은 등록 화면뿐이다.
     * q 는 site.name LIKE, 없으면 전체. status 를 가리지 않는다(DRAFT 포함).
     */
    @GetMapping
    public ApiResponse<PageResponse<AdminSiteListResponse>> list(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false)
            @Pattern(regexp = "^(DRAFT|ACTIVE|INACTIVE)$", message = "지원하지 않는 상태입니다.")
            String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        // status 를 주지 않으면 전부 나온다 — 등록 직후의 DRAFT 를 찾는 것이 이 목록의 본래 쓰임이다.
        return ApiResponse.ok(siteService.searchForAdmin(q, status, page, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SiteResponse> create(@Valid @RequestBody AdminSiteSaveRequest request) {
        return ApiResponse.ok(adminSiteService.create(request));
    }

    @PutMapping("/{siteId}")
    public ApiResponse<SiteResponse> update(@PathVariable @Positive Long siteId,
                                            @Valid @RequestBody AdminSiteSaveRequest request) {
        return ApiResponse.ok(adminSiteService.update(siteId, request));
    }

    /** 상태 전이만 한다. ACTIVE 조건·INACTIVE 조건은 AdminSiteService 가 검사한다. */
    @PatchMapping("/{siteId}/status")
    public ApiResponse<Void> status(@PathVariable @Positive Long siteId,
                                    @Valid @RequestBody StatusChangeRequest request) {
        adminSiteService.changeStatus(siteId, request);
        return ApiResponse.ok();
    }

    @PostMapping("/{siteId}/viewpoints")
    public ApiResponse<Void> viewpoint(@PathVariable @Positive Long siteId,
                                       @Valid @RequestBody SiteViewpointSaveRequest request) {
        adminSiteService.upsertViewpoint(siteId, request);
        return ApiResponse.ok();
    }

    @PostMapping("/{siteId}/badges")
    public ApiResponse<Void> badge(@PathVariable @Positive Long siteId,
                                   @Valid @RequestBody SiteBadgeSaveRequest request) {
        adminSiteService.upsertBadge(siteId, request);
        return ApiResponse.ok();
    }

    /**
     * 현장에 걸 QR 발급. validitySeconds 를 길게 주면 상설 안내판용, 짧게 주면 회전 표시기용이다.
     * 토큰에 현재 qr_version 이 서명되므로, 아래 재발급(bump)을 한 번 하면 이전 QR 은 전부 무효가 된다.
     */
    @PostMapping("/{siteId}/qr")
    public ApiResponse<QrIssueResponse> issueQr(
            @PathVariable Long siteId,
            @RequestParam(required = false) Long validitySeconds) {

        Site site = siteService.getActiveEntity(siteId);
        CourseSiteRow slot = courseService.requireSlotBySite(siteId);

        QrTokenProvider.QrToken token = validitySeconds == null
                ? qrTokenProvider.issue(siteId, site.getQrVersion())
                : qrTokenProvider.issue(siteId, site.getQrVersion(), validitySeconds);

        return ApiResponse.ok(new QrIssueResponse(
                site.getSiteId(),
                slot.getCourseSiteId(),
                site.getName(),
                token.token(),
                toBase64Png(checkinUrl(token.token())),
                site.getQrVersion(),
                toLocal(token.issuedAt()),
                toLocal(token.expiresAt())));
    }

    /**
     * QR 유출 시. 버전을 올리면 그 사찰에 뿌려 둔 QR 이 한 번에 무효가 된다 —
     * 종이를 회수하기 전에 피해를 끊을 수 있다.
     */
    @PostMapping("/{siteId}/qr/rotate")
    public ApiResponse<Integer> rotateQr(@PathVariable Long siteId) {
        return ApiResponse.ok(siteService.bumpQrVersion(siteId));
    }

    /**
     * QR 이미지에 <b>굽는 내용</b>. 토큰만 굽지 않고 체크인 주소를 굽는다.
     * <p>
     * 기본 카메라로 찍은 사람이 바로 열 수 있어야 하기 때문이다 — 토큰만 굽혀 있으면
     * 카메라가 "djF8MXwx…" 라는 글자를 보여 주고 거기서 끝난다. 앱을 깔지 않은 사람은
     * 무엇을 해야 하는지 알 길이 없다. 프론트 라우트 {@code /checkin?token=} 은 이미 있다.
     * <p>
     * 서버가 받는 것은 여전히 <b>토큰만</b>이다({@code QrVerifyRequest.qrToken}) —
     * 주소에서 토큰을 뽑는 일은 프론트가 한다. 서버가 URL 을 받으면 파싱이 하나 더 생기고,
     * 그 파싱은 사용자 입력을 다루는 자리가 된다.
     */
    private String checkinUrl(String token) {
        String base = appProperties.frontendUrl();
        if (base == null || base.isBlank()) {
            throw new IllegalStateException("app.frontend-url 이 비어 있다 — QR 에 구울 주소가 없다");
        }
        String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return trimmed + "/checkin?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private String toBase64Png(String content) {
        try {
            Map<EncodeHintType, Object> hints = Map.of(
                    EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name(),
                    // 사찰 밖에 붙는 인쇄물이라 젖거나 긁혀도 읽히도록 복원 수준을 높게 잡는다.
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H,
                    EncodeHintType.MARGIN, 1);

            BitMatrix matrix = new QRCodeWriter()
                    .encode(content, BarcodeFormat.QR_CODE, QR_SIZE_PX, QR_SIZE_PX, hints);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());

        } catch (WriterException | IOException e) {
            throw new BusinessException(ErrorCode.COMMON_5000, "QR 이미지 생성에 실패했습니다.");
        }
    }

    private LocalDateTime toLocal(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
