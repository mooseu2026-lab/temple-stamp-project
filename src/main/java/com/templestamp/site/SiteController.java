package com.templestamp.site;

import com.templestamp.global.response.ApiResponse;
import com.templestamp.global.response.PageResponse;
import com.templestamp.global.security.AuthenticatedUser;
import com.templestamp.global.type.Tier;
import com.templestamp.global.web.LocaleUtil;
import com.templestamp.site.dto.SiteGuideResponse;
import com.templestamp.site.dto.SitePageResponse;
import com.templestamp.site.dto.SiteResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사찰 기본 정보 · 싱글페이지 · 가는 법.
 * 인가 등급: 전부 공개. 다만 /page 는 토큰이 있으면 개인화된다(계층·언어·verifyState·노출 이력).
 * SecurityConfig: GET /api/sites/** permitAll.
 * <p>
 * 언어 우선순위는 <b>쿼리 locale → Accept-Language → users.locale → ko</b> 다.
 * 컨트롤러는 앞의 둘만 합쳐 힌트 하나로 만들고, 뒤의 둘은 로그인 여부를 아는 Service 가 판단한다.
 */
@RestController
@RequestMapping("/api/sites")
@RequiredArgsConstructor
@Validated
@Slf4j
public class SiteController {

    private final SiteService siteService;
    private final SitePageService sitePageService;
    private final SiteGuideService siteGuideService;

    /** GET /api/sites/{siteId} — 좌표·반경·QR 힌트. 없으면 404 SITE-4040 */
    @GetMapping("/{siteId}")
    public ApiResponse<SiteResponse> site(
            @PathVariable @Positive Long siteId,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        String locale = LocaleUtil.resolve(acceptLanguage);
        log.debug("GET /api/sites/{} locale={}", siteId, locale);
        return ApiResponse.ok(siteService.get(siteId, locale));
    }

    /**
     * GET /api/sites/{siteId}/page?target=RIDER&amp;locale=en
     * <p>
     * courseId 를 받지 않는다 — course_site.site_id 에 UNIQUE 가 걸려 있어 siteId 만으로
     * 코스·자리·구절이 결정되기 때문이다.
     * <p>
     * target 이 없으면 Service 가 users.tier → AGE30 순으로 정한다. 요청 계층에 문구가 없으면
     * AGE30 으로 한 번 물러나고, 그래도 없으면 SITE-5001 이다.
     * 매번 새 문구가 나가야 하므로 캐시하지 않는다.
     */
    @GetMapping("/{siteId}/page")
    public ApiResponse<SitePageResponse> page(
            @PathVariable @Positive Long siteId,
            @RequestParam(required = false) Tier target,
            @RequestParam(required = false) String locale,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
            @AuthenticationPrincipal AuthenticatedUser user) {
        String localeHint = LocaleUtil.hint(locale, acceptLanguage);
        Long userId = user == null ? null : user.userId();
        log.debug("GET /api/sites/{}/page target={} localeHint={} userId={}",
                siteId, target, localeHint, userId);
        return ApiResponse.ok(sitePageService.getPage(siteId, target, localeHint, userId));
    }

    /**
     * GET /api/sites/{siteId}/guide — 가는 법 7자리. 항상 7칸이고 없는 자리도 빠지지 않는다.
     * <p>
     * ★ Accept-Language 를 받지 않는다. 사전(shrine_element)이 한국어 한 벌뿐이라, 지금 언어를 받으면
     * 절 이름만 번역되고 본문은 한국어인 반쪽 응답이 나간다. 형제 API 와 규칙이 다르므로 프론트에
     * 알려야 한다. 번역 원고가 오면 shrine_element_i18n + COALESCE 한 줄로 붙는다.
     */
    @GetMapping("/{siteId}/guide")
    public ApiResponse<SiteGuideResponse> guide(@PathVariable @Positive Long siteId) {
        log.debug("GET /api/sites/{}/guide", siteId);
        return ApiResponse.ok(siteGuideService.getGuide(siteId));
    }
}
