// src/main/java/com/templestamp/admin/AdminSiteService.java
package com.templestamp.admin;

import com.templestamp.admin.dto.AdminCourseSaveRequest;
import com.templestamp.admin.dto.AdminSiteSaveRequest;
import com.templestamp.admin.dto.SiteBadgeSaveRequest;
import com.templestamp.admin.dto.SiteElementSaveRequest;
import com.templestamp.admin.dto.SiteViewpointSaveRequest;
import com.templestamp.admin.dto.StatusChangeRequest;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.global.error.ErrorResponse;
import com.templestamp.site.Site;
import com.templestamp.site.SiteBadgeMapper;
import com.templestamp.site.SiteElementMapper;
import com.templestamp.site.SiteI18nMapper;
import com.templestamp.site.SiteMapper;
import com.templestamp.site.SiteService;
import com.templestamp.site.SiteViewpointMapper;
import com.templestamp.site.dto.SiteResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 관리자 사찰 등록·수정·상태·부속 정보. 트랜잭션 경계는 여기다.
 * <p>
 * 규칙
 * <ol>
 *   <li>i18n 에 ko 행이 반드시 있어야 한다. 없으면 400 COMMON-4000 + {@code fields[i18n]}.
 *       {@code site.name} 이 ko 이름에서 오고, 소개문의 원문도 ko 행에만 있기 때문이다
 *       ({@code site} 테이블에는 description 컬럼이 없다).</li>
 *   <li>i18n 저장은 UPSERT(uk_site_i18n). 삭제 후 재삽입 금지 — 보내지 않은 언어는 건드리지 않는다.</li>
 *   <li>ACTIVE 전환 조건: qr_location_hint 와 ko 행이 있어야 한다. 못 채우면 409 ADMIN-4092.
 *       좌표·반경은 {@code @Valid} 가 이미 필수로 막는다.</li>
 *   <li>INACTIVE 전환: 이 사찰이 ACTIVE 코스에 배정돼 있으면 409 COURSE-4092 — 코스를 먼저 내려야 한다.</li>
 * </ol>
 * 등록(PUT)과 상태 전이(PATCH)를 나눈 이유: 한 요청에서 둘을 같이 하면 "조건을 못 채운 채 ACTIVE 로 올리는"
 * 경로가 생긴다. 상태는 조건 검사를 통과해야만 바뀐다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminSiteService {

    private final SiteMapper siteMapper;
    private final SiteI18nMapper siteI18nMapper;
    private final SiteElementMapper siteElementMapper;
    private final SiteViewpointMapper siteViewpointMapper;
    private final SiteBadgeMapper siteBadgeMapper;
    private final SiteService siteService;   // 저장 후 응답은 사용자용 SiteResponse 를 재사용한다
    private final AdminCourseService adminCourseService;   // 시더가 코스를 만들 때만 쓴다

    @Transactional
    public SiteResponse create(AdminSiteSaveRequest req) {
        requireKo(req);
        return siteService.save(null, req);   // save 가 관리자용 응답(DRAFT 포함)을 돌려준다
    }

    @Transactional
    public SiteResponse update(Long siteId, AdminSiteSaveRequest req) {
        requireKo(req);
        requireSite(siteId);
        return siteService.save(siteId, req);
    }

    @Transactional
    public void changeStatus(Long siteId, StatusChangeRequest req) {
        Site site = requireSite(siteId);

        switch (req.status()) {
            case Site.ACTIVE -> {
                boolean ready = site.getQrLocationHint() != null
                        && !site.getQrLocationHint().isBlank()
                        && siteI18nMapper.exists(siteId, "ko");
                if (!ready) {
                    throw new BusinessException(ErrorCode.ADMIN_4092,
                            "QR 위치 안내와 ko 언어 정보를 채워야 공개할 수 있습니다.");
                }
            }
            // 상태를 낮추는 전이는 둘 다 같은 검사를 받는다. 코스의 ACTIVE 조건이 "사찰 5곳 전부 ACTIVE"
            // 인데 뒤에서 한 곳이 빠지면 그 조건이 조용히 깨진다 — INACTIVE 든 DRAFT 든 결과는 같다.
            case Site.INACTIVE, Site.DRAFT -> {
                if (siteMapper.countActiveCourseAssignments(siteId) > 0) {
                    throw new BusinessException(ErrorCode.COURSE_4092,
                            "이 사찰이 배정된 코스를 먼저 비활성화해 주세요.");
                }
            }
            default -> {
                // @Pattern 이 DRAFT/ACTIVE/INACTIVE 만 통과시키므로 여기에 오는 값은 없다.
            }
        }

        siteMapper.updateStatus(siteId, req.status());
        log.info("admin site status siteId={} -> {}", siteId, req.status());
    }

    @Transactional
    public void upsertViewpoint(Long siteId, SiteViewpointSaveRequest req) {
        requireSite(siteId);
        siteViewpointMapper.upsert(siteId, req.sortNo(), req.locationDesc(), req.bestTime(), req.whatToSee());
    }

    @Transactional
    public void upsertBadge(Long siteId, SiteBadgeSaveRequest req) {
        requireSite(siteId);
        siteBadgeMapper.upsert(siteId, req.badgeType(), req.description());
    }

    /**
     * v4 시더 전용 — 코스 하나를 만들고 courseId 만 돌려준다.
     * <p>
     * 시더가 {@link AdminCourseService} 를 직접 부르지 않고 여기를 거치는 이유는 하나다.
     * 시더가 손대는 관리자 쓰기 경로를 이 클래스 한 곳으로 모아 두면, 나중에 "시드가 무엇을 만드는가" 를
     * 여기만 보고 알 수 있다. 검증(5곳·position 중복·다른 코스 소유)은 그대로 통과해야 한다.
     */
    @Transactional
    public Long createCourseForSeed(AdminCourseSaveRequest req) {
        return adminCourseService.create(req).courseId();
    }

    /* ---------------- 참배 요소 (v4) ---------------- */

    /**
     * 사찰의 7자리 중 "있는 것" 만 골라 UPSERT 한다. 보내지 않은 자리는 건드리지 않는다.
     * 없는 코드는 400 COMMON-4000 + {@code fields[elementCode]} — 오타를 조용히 무시하면
     * 관리자는 넣었다고 믿는데 「가는 법」에는 안 나온다.
     */
    @Transactional
    public void upsertElements(Long siteId, SiteElementSaveRequest req) {
        requireSite(siteId);
        Map<String, Long> codeToId = elementCodeToId();

        List<ErrorResponse.FieldError> unknown = req.items().stream()
                .map(SiteElementSaveRequest.Item::elementCode)
                .filter(code -> !codeToId.containsKey(code))
                .map(code -> new ErrorResponse.FieldError("elementCode", "없는 요소 코드입니다: " + code))
                .toList();
        if (!unknown.isEmpty()) {
            throw new BusinessException(ErrorCode.COMMON_4000, unknown);
        }

        for (SiteElementSaveRequest.Item it : req.items()) {
            siteElementMapper.upsert(siteId, codeToId.get(it.elementCode()), it.localName(), it.note());
        }
        log.info("admin site elements upserted siteId={} count={}", siteId, req.items().size());
    }

    /** 그 자리를 "없음" 으로 되돌린다. 사전 7자리는 그대로고 이 절의 보유 표시만 지운다. */
    @Transactional
    public void deleteElement(Long siteId, String elementCode) {
        requireSite(siteId);
        Long elementId = elementCodeToId().get(elementCode);
        if (elementId == null) {
            throw new BusinessException(ErrorCode.COMMON_4000,
                    List.of(new ErrorResponse.FieldError("elementCode", "없는 요소 코드입니다: " + elementCode)));
        }
        siteElementMapper.delete(siteId, elementId);
    }

    /** shrine_element 는 7행뿐이라 매번 읽어도 부담이 없다. */
    private Map<String, Long> elementCodeToId() {
        Map<String, Long> map = new LinkedHashMap<>();
        for (Map<String, Object> row : siteElementMapper.findDictionary()) {
            map.put((String) row.get("code"), ((Number) row.get("elementId")).longValue());
        }
        return map;
    }

    /* ---------------- 내부 ---------------- */

    /**
     * ko 행이 없으면 검증 오류 형식(COMMON-4000 + fields)으로 던진다 — 프론트가 폼 오류로 표시하도록.
     * {@code @Valid} 로는 잡을 수 없다. 목록 안의 어떤 원소가 특정 값을 가져야 한다는 조건이라
     * 원소 하나만 봐서는 알 수 없기 때문이다.
     */
    private void requireKo(AdminSiteSaveRequest req) {
        boolean hasKo = req.i18n().stream().anyMatch(b -> "ko".equals(b.locale()));
        if (!hasKo) {
            throw new BusinessException(ErrorCode.COMMON_4000,
                    List.of(new ErrorResponse.FieldError("i18n", "ko 언어 정보는 필수입니다.")));
        }
    }

    private Site requireSite(Long siteId) {
        return siteMapper.findById(siteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SITE_4040));
    }
}
