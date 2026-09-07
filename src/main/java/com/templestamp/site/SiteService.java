package com.templestamp.site;

import com.templestamp.admin.dto.AdminSiteSaveRequest;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.global.response.PageResponse;
import com.templestamp.admin.dto.AdminSiteListResponse;
import com.templestamp.site.dto.SiteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SiteService {

    private final SiteMapper siteMapper;
    private final SiteI18nMapper siteI18nMapper;

    public Site getEntity(Long siteId) {
        return siteMapper.findById(siteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SITE_4040));
    }

    /** 인증에 쓰는 조회. 협의가 끝난(ACTIVE) 사찰만 순례 대상이다. */
    public Site getActiveEntity(Long siteId) {
        return siteMapper.findActiveById(siteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SITE_4090));
    }

    /**
     * 공개 화면이 여는 사찰. ACTIVE 가 아니면 "없다"(404 SITE-4040)로 답한다.
     * 인증 진입점(getActiveEntity)이 409 SITE-4090 을 쓰는 것과 다르다 — 그쪽은 사찰의 존재를
     * 이미 아는 사용자가 도장을 찍으려는 자리라 "지금은 안 된다" 가 맞고,
     * 여기는 목록에서 내린 것을 링크로 들어온 자리라 "없다" 가 맞다.
     */
    public Site getPublicEntity(Long siteId) {
        return siteMapper.findActiveById(siteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SITE_4040));
    }

    public SiteResponse get(Long siteId, String locale) {
        return toResponse(getPublicEntity(siteId), locale);
    }

    /** 관리자 응답용. status 를 가리지 않는다 — 등록 직후 DRAFT 사찰을 그대로 돌려줘야 한다. */
    public SiteResponse getAnyStatus(Long siteId, String locale) {
        return toResponse(getEntity(siteId), locale);
    }

    public PageResponse<SiteResponse> search(String keyword, int page, int size, String locale) {
        String normalized = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        long total = siteMapper.countSearch(normalized);
        List<SiteResponse> items = siteMapper.search(normalized, page * size, size).stream()
                .map(site -> toResponse(site, locale))
                .toList();
        return PageResponse.of(items, page, size, total);
    }

    /**
     * 관리자 목록. 공개 검색과 달리 status 를 가리지 않는다 — 등록 직후의 DRAFT 사찰을 찾아야 하기 때문이다.
     * 이름은 ko 원문(site.name)으로만 본다. 관리자 화면은 한국어이고, 번역본으로 찾을 일이 없다.
     * <p>
     * 응답이 공개용({@code SiteResponse})과 다른 이유는 {@code status} 하나다 — 관리자는 목록에서
     * 공개 여부를 봐야 하고, 공개 응답에 그 필드를 넣으면 사용자 화면에도 따라 나간다.
     */
    public PageResponse<AdminSiteListResponse> searchForAdmin(String keyword, String status, int page, int size) {
        String normalized = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        String statusFilter = (status == null || status.isBlank()) ? null : status;
        long total = siteMapper.countSearchAnyStatus(normalized, statusFilter);
        List<AdminSiteListResponse> items =
                siteMapper.searchAnyStatus(normalized, statusFilter, page * size, size).stream()
                        .map(AdminSiteListResponse::from)
                        .toList();
        return PageResponse.of(items, page, size, total);
    }

    /**
     * 요청 언어 번역이 없으면 ko 로, ko 도 없으면 site.name 원문으로 떨어뜨린다.
     */
    private SiteResponse toResponse(Site site, String locale) {
        String name = site.getName();
        String description = null;

        var i18n = siteI18nMapper.findBySiteIdAndLocale(site.getSiteId(), locale)
                .or(() -> siteI18nMapper.findBySiteIdAndLocale(site.getSiteId(), "ko"))
                .orElse(null);
        if (i18n != null) {
            name = i18n.getName();
            description = i18n.getDescription();
        }

        return new SiteResponse(site.getSiteId(), name, description,
                site.getLatitude(), site.getLongitude(),
                site.getVerifyRadius(), site.getQrLocationHint());
    }

    /* ---------------- 관리자 ---------------- */

    /**
     * 사찰 등록/수정. siteId 가 null 이면 신규다.
     * site.name 은 ko 이름을 쓰고, 언어별 이름·설명은 site_i18n 에 따로 넣는다.
     */
    @Transactional
    public SiteResponse save(Long siteId, AdminSiteSaveRequest request) {
        Site site = Site.builder()
                .siteId(siteId)
                .name(request.primaryName())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .verifyRadius(request.verifyRadius())
                .qrLocationHint(request.qrLocationHint())
                .parkingInfo(request.parkingInfo())
                .accessInfo(request.accessInfo())
                .mealAvailable(request.mealAvailable())
                // status 를 세우지 않는다. INSERT 는 DB DEFAULT 로 DRAFT 가 되고,
                // UPDATE 는 SET 절에서 status 를 빼 두어 지금 값이 유지된다.
                // 공개 여부를 바꾸는 길은 PATCH /api/admin/sites/{id}/status 하나뿐이다 —
                // 그래야 ACTIVE 전환 조건(qr 힌트·ko 행)을 건너뛸 수 없다.
                .build();

        if (siteId == null) {
            siteMapper.save(site);
        } else {
            getEntity(siteId);
            siteMapper.update(site);
        }

        for (AdminSiteSaveRequest.I18nBlock block : request.i18n()) {
            siteI18nMapper.upsert(SiteI18n.builder()
                    .siteId(site.getSiteId())
                    .locale(block.locale())
                    .name(block.name())
                    .description(block.description())
                    .build());
        }

        return toResponse(getEntity(site.getSiteId()), "ko");   // 관리자 응답 — DRAFT 도 그대로 돌려준다
    }

    /** QR 이 유출됐을 때. 버전을 올리면 그 사찰에 뿌려 둔 QR 이 한 번에 무효가 된다. */
    @Transactional
    public int bumpQrVersion(Long siteId) {
        getEntity(siteId);
        siteMapper.bumpQrVersion(siteId);
        return getEntity(siteId).getQrVersion();
    }
}
