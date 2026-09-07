# 챕터 3 — 카카오 장소 검색 + 관리자 사찰·코스 등록 (F-04 관리자 흐름 7 · 명세 §4 "지도 검색"·"관리자")

> 작성일 2026-09-05 · 스택 고정: Spring Boot 3.5.16 · Java 21 · Lombok · MyBatis 3.0.5 · MySQL 8 · record · 담당 학생 B(kakao·admin)
> 파일 위치: `backend/docs/textbook/ch3.md`. 이 챕터의 코드는 **저장소에 이미 대부분 있다**(의견03). 교재 코드는 "이렇게 되어 있어야 한다"는 기준이고, Claude Code가 저장소와 대조해 다른 곳만 맞춘다.

---

## 0. 의견06 결정 (2026-09-05 확정) — 챕터 2 잔여

| # | 질의 | 결정 | 근거 |
|---|---|---|---|
| 1 | `GET /health` permitAll 추가 | **유지.** `application-prod.yml`에도 `management.endpoints.web.exposure.include: health` 한 번 더 고정 | 없으면 Render 헬스체크가 401. prod 재고정은 `base-path: /` 사고 방지 |
| 2-1 | `steps[].position` vs `sortNo` | **`position` 확정.** Postman 단언 한 줄 수정 | 구현·테스트·v2 문서 3곳 일치. `course_site.position`과 낱말이 겹치지만 응답 객체가 달라 실무 혼동은 없음 — 교재에 "가는 법의 position은 참배 순서 1~7, 코스의 position은 자리 1~5"로 명기 |
| 2-2 | `X-Request-Id` 32자 | **32자 유지.** Postman 단언을 `lengthOf(32)`로 | 8자는 생일 충돌. 로그 가독성은 출력만 앞 8자 |
| 3-1 | `jq` | **준비물 문서에 넣는다** (`winget install jqlang.jq`). `backend/docs/setup.md` 신설 | node shim은 유지보수 대상이 하나 늘어남 |
| 3-2 | 한글 본문 | **`--data-binary @파일`로 변경.** `docs/verify/body/signup.json`(UTF-8) 분리 | 한글 서비스의 검증 세트가 한글을 피하면 한글 경로를 못 잡는다 |
| 4 | `/api/regions/` → 401 | 프론트 전달 목록에 추가 (`backend/docs/frontend-handoff.md` 신설, 의견03 §10 + 이 줄) | |
| 5-① | `GET /api/sites` 검색 | **`[AUDIT]` 표기 후 이 챕터에서 관리자 전용 `GET /api/admin/sites?q=&page=&size=`로 이동.** 공개 경로에서 제거 | 사용자 흐름은 권역→코스→사찰이라 전체 검색이 없다. 관리자 등록 화면에는 필요 |
| 5-② | `RegionResponse.code` 없음 | **추가한다** (Row·XML·Response 세 줄) | DB에 있고 v2 명세에 있다. 프론트가 `JEJU` 같은 코드로 분기해야 지도 9권역 점등(인트로 3단)이 id 하드코딩 없이 된다 |
| 5-③ | `site.description` 없음 | **컬럼을 추가하지 않는다** (인수인계 §4 결정 번복). 대신 사찰 저장 시 **`ko` i18n 행 필수**를 Service가 검증 | 원문이 두 곳이면 어느 쪽이 진실인지 매번 물어야 한다. ko 행 강제가 "번역 없으면 null" 문제를 원천에서 막는다 |
| 7-① | 스탬프 종단 테스트 | 챕터 5 **첫 STEP**을 "의견91 수기 표 → 테스트 이식"으로 고정 | |
| 7-② | `stamp.expansion_phrase_id` 운영 ALTER | 배포 체크리스트(`backend/docs/deploy-checklist.md` 신설)에 첫 항목으로 | |

---

## 1. 사용자 흐름 ↔ 프로그램 흐름 (전체명세 2-1 흐름 7 · 관리자)

```
[관리자]  사찰 이름 입력 "통도사"        →  검색 결과에서 선택        →  폼 저장(ko 필수, en/ja/zh 선택)  →  코스 5곳 배정  →  ACTIVE 전환
             │                                 │                            │                                 │                  │
[요청]   GET /api/admin/kakao/places      (프론트 상태)              POST /api/admin/sites               POST /api/admin/courses   PATCH .../status
             │  ?query=통도사                                          PUT  /api/admin/sites/{id}          PUT  .../courses/{id}
             ▼                                                        POST .../sites/{id}/viewpoints       PUT  /api/admin/site-distances
[Security] hasRole("ADMIN") — /api/admin/** 전부. 토큰 없음 401 AUTH-4013, USER 권한 403 AUTH-4032
             ▼
[CoordinateFieldGuard]  /api/admin/sites/** 만 좌표 허용(화이트리스트). 그 외 admin 경로에 latitude 가 실리면 400 COMMON-4001
             ▼
[Controller]  @Valid 형식 검증(범위·길이·5곳) → Service
             ▼
[Service]   @Transactional — site UPSERT + site_i18n UPSERT(ko 없으면 400) / course + course_site 5행 / 상태 전이 조건 검사
             ▼
[Kakao]     서버만 REST 키 보유. x·y → longitude·latitude 로 바꿔 site 컬럼명과 맞춤. 키 없음 503 KAKAO-5030, 통신 실패 503 KAKAO-5031
```

**핵심 한 줄**: 사용자 화면의 지도는 이 API를 쓰지 않는다(프론트가 JavaScript 키로 직접 그림). 이 챕터의 카카오는 **관리자가 좌표를 손으로 안 치게 하는 도구**다.

---

## 2. 저장소와 명세의 경로 차이 — 이 챕터의 확정 경로

| 명세 §4 | 저장소(의견03) | 확정 |
|---|---|---|
| `GET /api/admin/kakao/places?query=&page=&size=` | `GET /api/admin/sites/place-search` | **명세 경로.** 카카오는 kakao 도메인의 것이고 사찰 등록 외에도 쓰인다 |
| `POST /api/admin/sites` · `PUT /{siteId}` | 있음 | 유지. **i18n은 본문 중첩**(AdminSiteSaveRequest.I18nBlock) — 명세의 별도 `POST /{id}/i18n`은 만들지 않는다 |
| `PATCH /api/admin/sites/{siteId}/status` | 없음 | **추가** |
| `POST /api/admin/sites/{siteId}/viewpoints` · `/badges` | 없음 | **추가** (UPSERT, uk 기준) |
| `POST /api/admin/courses` · `PUT /{courseId}` | 있음 | 유지. **사찰 5곳은 본문 중첩**(AdminCourseSaveRequest.SiteSlot) — 별도 `PUT /{id}/sites` 만들지 않음 |
| `PATCH /api/admin/courses/{courseId}/status` | 없음 | **추가** |
| `PUT /api/admin/site-distances` | 없음 | **추가.** 챕터 5 이동시간 심사의 입력 |
| `GET /api/admin/sites?q=&page=&size=` | 공개 `GET /api/sites`로 존재 | **관리자로 이동**(의견06 5-①) |
| `POST /api/admin/sites/{id}/qr` · `/qr/rotate` | 있음 | **챕터 5**(stamp 도메인, QrTokenProvider와 함께) |

에러코드(명세 §7 + 090501 §5): `COURSE-4001` 다른 코스의 자리 · `COURSE-4041` 코스 없음 · `COURSE-4042` 자리 없음 · `COURSE-4092` 비활성 코스 · `COURSE-4093` 사찰 중복 배정 · `SITE-4040` 사찰 없음 · `KAKAO-5030` 키 미설정 · `KAKAO-5031` 카카오 통신 실패 · `ADMIN-4092` 처리 불가 상태. 생성자 순서 **(status, code, message)**.

---

## [기본 35] KakaoProperties + KakaoMapConfig — 키는 서버만, RestClient 한 개

**파일** `src/main/java/com/templestamp/kakao/KakaoProperties.java`
```java
// src/main/java/com/templestamp/kakao/KakaoProperties.java
package com.templestamp.kakao;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * kakao.* 바인딩. REST 키는 .env.local 의 KAKAO_REST_API_KEY 에서 온다 — 커밋되는 .env 에는 절대 넣지 않는다.
 * 키가 비어 있어도 기동은 막지 않는다(RequiredEnvCheck 대상 아님): 관리자 검색만 503 이고 나머지 서비스는 정상이어야 하므로.
 * TempleStampApplication 의 @ConfigurationPropertiesScan 이 읽는다.
 */
@ConfigurationProperties(prefix = "kakao")
public record KakaoProperties(
        String restKey,          // kakao.rest-key ← ${KAKAO_REST_API_KEY:}  (저장소 기존 키 이름 유지. 콜론 뒤가 비어 있어 미설정이면 "" 로 바인딩)
        String baseUrl           // kakao.base-url = https://dapi.kakao.com  (테스트에서 MockWebServer 주소로 바꿔 끼우기 위해 설정으로 뺌)
) {
    /** 키가 있는가. 공백만 있는 것도 없는 것으로 본다 */
    public boolean hasKey() { return restKey != null && !restKey.isBlank(); }
}
```

**파일** `src/main/java/com/templestamp/kakao/KakaoMapConfig.java`
```java
// src/main/java/com/templestamp/kakao/KakaoMapConfig.java
package com.templestamp.kakao;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 카카오 로컬 API 전용 RestClient 빈 하나. 인증 헤더 "Authorization: KakaoAK {키}" 는 여기서 한 번만 붙인다.
 * RestClient 는 Spring 6.1+ 의 동기 HTTP 클라이언트 — RestTemplate 후속. 빌더로 baseUrl·기본 헤더를 고정해 두면
 * Service 는 경로와 쿼리만 쓴다.
 */
@Configuration
@RequiredArgsConstructor
public class KakaoMapConfig {

    private final KakaoProperties props;

    @Bean
    public RestClient kakaoRestClient() {
        return RestClient.builder()
                .baseUrl(props.baseUrl())                                   // https://dapi.kakao.com
                .defaultHeader("Authorization", "KakaoAK " + props.restKey())   // 키가 "" 여도 빈은 만든다 — 호출 시 Service 가 먼저 막는다
                .build();
    }
}
```

`application.yml` append:
```yaml
kakao:
  rest-key: ${KAKAO_REST_API_KEY:}    # 저장소 기존 이름 유지(의견 ch3-admin §4-2). 비어 있으면 관리자 검색만 503 KAKAO-5030
  base-url: https://dapi.kakao.com
```

**[담당: Claude Code]** · **예성 직접**: developers.kakao.com → 내 애플리케이션 → 앱 추가 → 앱 키 중 **REST API 키**를 `.env.local`에 `KAKAO_REST_API_KEY=...`로. (JavaScript 키는 프론트용, 다른 키다.)

---

## [기본 36] KakaoMapService — 검색 → 우리 규격 변환

**파일** `src/main/java/com/templestamp/kakao/KakaoMapService.java`
```java
// src/main/java/com/templestamp/kakao/KakaoMapService.java
package com.templestamp.kakao;

import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.kakao.dto.KakaoPlaceResponse;
import com.templestamp.kakao.dto.KakaoPlaceSearchResult;
import com.templestamp.kakao.dto.KakaoSearchRawResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 카카오 로컬 "키워드로 장소 검색" — GET /v2/local/search/keyword.json?query=&page=&size=
 *   page 1~45, size 1~15 (카카오 제한). 범위는 컨트롤러 @Min/@Max 가 먼저 막는다.
 * DB 를 건드리지 않으므로 @Transactional 없음. 결과는 저장하지 않는다 — 관리자가 고른 것만 site 로 들어간다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KakaoMapService {

    private final KakaoProperties props;
    private final RestClient kakaoRestClient;                  // KakaoMapConfig 의 빈. 이름으로 주입된다

    public KakaoPlaceSearchResult search(String query, int page, int size) {
        if (!props.hasKey()) {
            // 키가 없는 것은 "카카오가 잘못 응답한 것"(502) 이 아니라 "우리가 아직 준비 안 됨"(503). 090501 §5
            throw new BusinessException(ErrorCode.KAKAO_5030);
        }
        try {
            KakaoSearchRawResponse raw = kakaoRestClient.get()
                    .uri(b -> b.path("/v2/local/search/keyword.json")
                            .queryParam("query", query)             // RestClient 가 URL 인코딩 — 한글 그대로 넘긴다
                            .queryParam("page", page)
                            .queryParam("size", size)
                            .build())
                    .retrieve()
                    .body(KakaoSearchRawResponse.class);           // documents[] + meta 를 record 로 역직렬화 (@JsonProperty snake_case)

            if (raw == null || raw.documents() == null) {          // 200 인데 본문이 비는 경우 방어
                throw new BusinessException(ErrorCode.KAKAO_5031);
            }
            var places = raw.documents().stream()
                    .map(KakaoPlaceResponse::from)                  // x·y(String) → longitude·latitude(BigDecimal). site 컬럼명과 통일
                    .toList();
            log.debug("kakao search query={} page={} size={} total={}", query, page, size, raw.meta().totalCount());
            return KakaoPlaceSearchResult.of(places, raw.meta());
        } catch (RestClientException e) {
            // 4xx/5xx·타임아웃·역직렬화 실패 전부 여기. 키 값 자체는 절대 로그에 남기지 않는다
            log.warn("kakao search failed query={} cause={}", query, e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.KAKAO_5031);
        }
    }
}
```

**파일** `src/main/java/com/templestamp/admin/AdminKakaoController.java`
```java
// src/main/java/com/templestamp/admin/AdminKakaoController.java
package com.templestamp.admin;

import com.templestamp.global.response.ApiResponse;
import com.templestamp.kakao.KakaoMapService;
import com.templestamp.kakao.dto.KakaoPlaceSearchResult;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * GET /api/admin/kakao/places?query=통도사&page=1&size=15  — ADMIN 전용(SecurityConfig [admin] 구역).
 * 응답은 KakaoPlaceSearchResult{ places[], totalCount, end }. 관리자가 한 건을 골라 AdminSiteSaveRequest 의 latitude/longitude 에 넣는다.
 */
@RestController
@RequestMapping("/api/admin/kakao")
@RequiredArgsConstructor
@Validated
@Slf4j
public class AdminKakaoController {

    private final KakaoMapService kakaoMapService;

    @GetMapping("/places")
    public ApiResponse<KakaoPlaceSearchResult> places(
            @RequestParam @NotBlank @Size(max = 100) String query,          // 빈 문자열은 카카오도 400 을 주지만 우리가 먼저 COMMON-4000
            @RequestParam(defaultValue = "1")  @Min(1) @Max(45) int page,   // 카카오 상한
            @RequestParam(defaultValue = "15") @Min(1) @Max(15) int size) {
        log.debug("GET /api/admin/kakao/places query={} page={} size={}", query, page, size);
        return ApiResponse.ok(kakaoMapService.search(query, page, size));
    }
}
```

**왜 이렇게 하는가** REST 키와 JavaScript 키는 다른 키다. REST 키는 서버 안에만 있고 응답 어디에도 실리지 않는다. 로그도 `e.getClass().getSimpleName()`만 — 카카오 4xx 본문에 키 일부가 섞여 나올 수 있어 예외 메시지를 통째로 찍지 않는다.

**자주 만나는 오류** 401 from Kakao → 키를 JavaScript 키로 넣었다 / 앱에 "카카오맵" 서비스 활성화를 안 했다. 둘 다 우리 응답은 `KAKAO-5031`이고 원인은 서버 로그 `warn`으로 본다.

**확인포인트** 키 없이 → 503 `KAKAO-5030` / 키 넣고 `query=통도사` → `places[0].placeName` 에 "통도사", `latitude` 35.4x

**[담당: Claude Code]**

---

## [기본 37] 관리자 사찰 — AdminSiteService (UPSERT·ko 필수·상태 전이)

신규 DTO 3개(record, `admin/dto`):
```java
// src/main/java/com/templestamp/admin/dto/StatusChangeRequest.java
package com.templestamp.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 사찰·코스 공용 상태 변경 요청. DRAFT→ACTIVE→INACTIVE, INACTIVE→ACTIVE 허용. ACTIVE 조건은 Service 가 검사 */
public record StatusChangeRequest(
        @NotBlank(message = "상태를 입력해주세요.")
        @Pattern(regexp = "^(DRAFT|ACTIVE|INACTIVE)$", message = "상태는 DRAFT, ACTIVE, INACTIVE 중 하나여야 합니다.")
        String status
) {}
```
```java
// src/main/java/com/templestamp/admin/dto/SiteViewpointSaveRequest.java
package com.templestamp.admin.dto;

import jakarta.validation.constraints.*;

/** 뷰포인트 1건 UPSERT — uk_site_viewpoint(site_id, sort_no). 같은 sortNo 로 다시 보내면 덮어쓴다 */
public record SiteViewpointSaveRequest(
        @NotNull @Min(1) @Max(3) Integer sortNo,                     // DB CHECK (1~3) 미러링
        @NotBlank @Size(max = 255) String locationDesc,
        @Size(max = 100) String bestTime,
        @Size(max = 255) String whatToSee
) {}
```
```java
// src/main/java/com/templestamp/admin/dto/SiteBadgeSaveRequest.java
package com.templestamp.admin.dto;

import jakarta.validation.constraints.*;

/** 뱃지 1건 UPSERT — uk_site_badge(site_id, badge_type) */
public record SiteBadgeSaveRequest(
        @NotBlank @Pattern(regexp = "^(FLOWER|GUARDIAN)$", message = "뱃지는 FLOWER 또는 GUARDIAN 입니다.") String badgeType,
        @Size(max = 500) String description
) {}
```

**파일** `src/main/java/com/templestamp/admin/AdminSiteService.java`
```java
// src/main/java/com/templestamp/admin/AdminSiteService.java
package com.templestamp.admin;

import com.templestamp.admin.dto.*;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.global.error.ErrorResponse;
import com.templestamp.site.*;
import com.templestamp.site.dto.SiteResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 관리자 사찰 등록·수정·상태·부속 정보. 트랜잭션 경계는 여기(규약 §7).
 * 규칙
 *  ① i18n 에 ko 행이 반드시 있어야 한다(의견06 5-③ 결정). 없으면 400 COMMON-4000 + fields[i18n].
 *  ② i18n 저장은 UPSERT(uk_site_i18n) — 삭제 후 재삽입 금지. 보내지 않은 언어는 건드리지 않는다(null=변경 없음 규약).
 *  ③ ACTIVE 전환 조건: 좌표·반경·ko 행 존재 + qr_location_hint 존재. 못 채우면 409 ADMIN-4092.
 *  ④ INACTIVE 전환: 이 사찰이 ACTIVE 코스에 배정돼 있으면 409 COURSE-4092 — 코스를 먼저 내려야 한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminSiteService {

    private final SiteMapper siteMapper;
    private final SiteI18nMapper siteI18nMapper;
    private final SiteViewpointMapper siteViewpointMapper;
    private final SiteBadgeMapper siteBadgeMapper;
    private final SiteService siteService;                 // 저장 후 응답은 사용자용 SiteResponse 재사용(DTO 배치표 방침)

    @Transactional
    public SiteResponse create(AdminSiteSaveRequest req) {
        requireKo(req);                                     // ① 형식 검증(@Valid)이 못 잡는 "ko 포함" 조건
        Site site = toEntity(req);                          // 도메인 클래스로 옮김. status 는 DRAFT 기본(DB DEFAULT)
        siteMapper.insert(site);                            // useGeneratedKeys → site.siteId 채워짐
        upsertI18n(site.getSiteId(), req.i18n());           // ②
        log.info("admin site created siteId={}", site.getSiteId());   // 이름은 로그에 안 남긴다 — 필요하면 id 로 찾는다
        return siteService.get(site.getSiteId(), "ko");
    }

    @Transactional
    public SiteResponse update(Long siteId, AdminSiteSaveRequest req) {
        requireKo(req);
        if (siteMapper.findById(siteId) == null) throw new BusinessException(ErrorCode.SITE_4040);
        Site site = toEntity(req);
        site.setSiteId(siteId);
        siteMapper.update(site);                            // 좌표·반경·힌트·라이더 정보. status 는 여기서 안 바꾼다(PATCH 전용)
        upsertI18n(siteId, req.i18n());
        return siteService.get(siteId, "ko");
    }

    @Transactional
    public void changeStatus(Long siteId, StatusChangeRequest req) {
        Site site = siteMapper.findById(siteId);
        if (site == null) throw new BusinessException(ErrorCode.SITE_4040);
        switch (req.status()) {                             // Java 21 switch — 문자열 분기. 세 값은 @Pattern 이 보장
            case "ACTIVE" -> {
                boolean ready = site.getQrLocationHint() != null && siteI18nMapper.exists(siteId, "ko");   // ③
                if (!ready) throw new BusinessException(ErrorCode.ADMIN_4092);
            }
            case "INACTIVE" -> {
                if (siteMapper.countActiveCourseAssignments(siteId) > 0) throw new BusinessException(ErrorCode.COURSE_4092);   // ④
            }
            default -> { /* DRAFT 로 되돌리기: 조건 없음 */ }
        }
        siteMapper.updateStatus(siteId, req.status());
        log.info("admin site status siteId={} → {}", siteId, req.status());
    }

    @Transactional
    public void upsertViewpoint(Long siteId, SiteViewpointSaveRequest req) {
        if (siteMapper.findById(siteId) == null) throw new BusinessException(ErrorCode.SITE_4040);
        siteViewpointMapper.upsert(siteId, req.sortNo(), req.locationDesc(), req.bestTime(), req.whatToSee());
    }

    @Transactional
    public void upsertBadge(Long siteId, SiteBadgeSaveRequest req) {
        if (siteMapper.findById(siteId) == null) throw new BusinessException(ErrorCode.SITE_4040);
        siteBadgeMapper.upsert(siteId, req.badgeType(), req.description());
    }

    // ── 내부 ──────────────────────────────────────────────────────────────────

    /** ko 행이 없으면 검증 오류 형식(COMMON-4000 + fields)으로 던진다 — 프론트가 폼 오류로 표시하도록 */
    private void requireKo(AdminSiteSaveRequest req) {
        boolean hasKo = req.i18n().stream().anyMatch(b -> "ko".equals(b.locale()));
        if (!hasKo) {
            throw new BusinessException(ErrorCode.COMMON_4000,
                    List.of(new ErrorResponse.FieldError("i18n", "ko 언어 정보는 필수입니다.")));
            // ※ BusinessException 에 fields 를 받는 생성자가 없으면 STEP 에서 추가한다(오버로드 append)
        }
    }

    private void upsertI18n(Long siteId, List<AdminSiteSaveRequest.I18nBlock> blocks) {
        for (var b : blocks) {
            siteI18nMapper.upsert(siteId, b.locale(), b.name(), b.description());   // ON DUPLICATE KEY UPDATE name=VALUES(name), description=VALUES(description)
        }
    }

    private Site toEntity(AdminSiteSaveRequest req) {
        Site s = new Site();
        s.setName(req.i18n().stream().filter(b -> "ko".equals(b.locale())).findFirst().get().name());   // site.name = ko 이름. requireKo 뒤라 get() 안전
        s.setLatitude(req.latitude());
        s.setLongitude(req.longitude());
        s.setVerifyRadius(req.verifyRadius());
        s.setQrLocationHint(req.qrLocationHint());
        s.setParkingInfo(req.parkingInfo());
        s.setAccessInfo(req.accessInfo());
        s.setMealAvailable(req.mealAvailable());
        return s;
    }
}
```

Mapper XML 추가분(`mapper/site/`, 기존 파일에 append):
```xml
<!-- SiteI18nMapper.xml -->
<insert id="upsert">
  INSERT INTO site_i18n (site_id, locale, name, description)
  VALUES (#{siteId}, #{locale}, #{name}, #{description})
  ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description)
  <!-- [RULE] 삭제 후 재삽입 금지. uk_site_i18n(site_id, locale) 이 갱신 기준 -->
</insert>
<select id="exists" resultType="boolean">
  SELECT COUNT(*) > 0 FROM site_i18n WHERE site_id = #{siteId} AND locale = #{locale}
</select>

<!-- SiteViewpointMapper.xml -->
<insert id="upsert">
  INSERT INTO site_viewpoint (site_id, sort_no, location_desc, best_time, what_to_see)
  VALUES (#{siteId}, #{sortNo}, #{locationDesc}, #{bestTime}, #{whatToSee})
  ON DUPLICATE KEY UPDATE location_desc = VALUES(location_desc), best_time = VALUES(best_time), what_to_see = VALUES(what_to_see)
</insert>

<!-- SiteBadgeMapper.xml -->
<insert id="upsert">
  INSERT INTO site_badge (site_id, badge_type, description)
  VALUES (#{siteId}, #{badgeType}, #{description})
  ON DUPLICATE KEY UPDATE description = VALUES(description)
</insert>

<!-- SiteMapper.xml -->
<update id="updateStatus">
  UPDATE site SET status = #{status} WHERE site_id = #{siteId}
</update>
<select id="countActiveCourseAssignments" resultType="int">
  SELECT COUNT(*) FROM course_site cs JOIN course c ON c.course_id = cs.course_id
   WHERE cs.site_id = #{siteId} AND c.status = 'ACTIVE'
</select>
```

**파일** `src/main/java/com/templestamp/admin/AdminSiteController.java`
```java
// src/main/java/com/templestamp/admin/AdminSiteController.java
package com.templestamp.admin;

import com.templestamp.admin.dto.*;
import com.templestamp.global.response.ApiResponse;
import com.templestamp.global.response.PageResponse;
import com.templestamp.site.SiteService;
import com.templestamp.site.dto.SiteResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * /api/admin/sites — ADMIN. CoordinateFieldGuard 화이트리스트라 본문에 latitude/longitude 가 실려도 통과한다(유일한 예외).
 * 생성 201, 나머지 200(규약 "신규 생성 201").
 */
@RestController
@RequestMapping("/api/admin/sites")
@RequiredArgsConstructor
@Validated
@Slf4j
public class AdminSiteController {

    private final AdminSiteService adminSiteService;
    private final SiteService siteService;

    /** 관리자 목록·검색 — 옛 공개 GET /api/sites 를 여기로 이동(의견06 5-①). q 는 site.name LIKE, 없으면 전체. status 무관(DRAFT 포함) */
    @GetMapping
    public ApiResponse<PageResponse<SiteResponse>> list(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "0")  @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(siteService.searchForAdmin(q, page, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)                                     // 201
    public ApiResponse<SiteResponse> create(@RequestBody @Valid AdminSiteSaveRequest req) {
        return ApiResponse.ok(adminSiteService.create(req));
    }

    @PutMapping("/{siteId}")
    public ApiResponse<SiteResponse> update(@PathVariable @Positive Long siteId,
                                            @RequestBody @Valid AdminSiteSaveRequest req) {
        return ApiResponse.ok(adminSiteService.update(siteId, req));
    }

    @PatchMapping("/{siteId}/status")
    public ApiResponse<Void> status(@PathVariable @Positive Long siteId,
                                    @RequestBody @Valid StatusChangeRequest req) {
        adminSiteService.changeStatus(siteId, req);
        return ApiResponse.ok();                                            // "data": null
    }

    @PostMapping("/{siteId}/viewpoints")
    public ApiResponse<Void> viewpoint(@PathVariable @Positive Long siteId,
                                       @RequestBody @Valid SiteViewpointSaveRequest req) {
        adminSiteService.upsertViewpoint(siteId, req);
        return ApiResponse.ok();
    }

    @PostMapping("/{siteId}/badges")
    public ApiResponse<Void> badge(@PathVariable @Positive Long siteId,
                                   @RequestBody @Valid SiteBadgeSaveRequest req) {
        adminSiteService.upsertBadge(siteId, req);
        return ApiResponse.ok();
    }
}
```

**자주 만나는 오류** `POST /api/admin/sites` 가 400 `COMMON-4001` → CoordinateFieldGuard 화이트리스트가 `/api/admin/sites/**` 가 아니라 `/api/admin/**` 전체거나, 반대로 빠져 있다. 인수인계 §4: **`/api/admin/**` 와 GET/DELETE 는 검사 제외** — 저장소 실제 값을 STEP 0에서 확인.

**[담당: Claude Code]**

---

## [기본 38] 관리자 코스 — AdminCourseService (5곳·자리·구절·중복·상태)

**파일** `src/main/java/com/templestamp/admin/AdminCourseService.java`
```java
// src/main/java/com/templestamp/admin/AdminCourseService.java
package com.templestamp.admin;

import com.templestamp.admin.dto.AdminCourseSaveRequest;
import com.templestamp.admin.dto.StatusChangeRequest;
import com.templestamp.course.*;
import com.templestamp.course.dto.CourseDetailResponse;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * 코스 = 사찰 정확히 5곳, position 1~5 한 번씩, verseNo == position (schema: "이 자리의 오관게 구절 (= position)").
 * 사찰은 정확히 한 코스에만(uk_course_site_site) — 위반은 DB 가 막고 여기서는 미리 검사해 409 COURSE-4093 으로 바꾼다.
 * course_site 5행 교체는 한 트랜잭션 안에서 DELETE→INSERT — 문구의 [RULE] 과 다르다: 자리는 "비면 안 되는 콘텐츠" 가 아니라 "구성" 이고,
 * 진행 중 순례(pilgrimage.status=IN_PROGRESS)가 있으면 교체 자체를 409 COURSE-4092 로 막으므로 중간 상태가 사용자에게 보이지 않는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminCourseService {

    private final CourseMapper courseMapper;
    private final CourseSiteMapper courseSiteMapper;
    private final RegionMapper regionMapper;
    private final CourseService courseService;             // 응답은 사용자용 CourseDetailResponse 재사용

    @Transactional
    public CourseDetailResponse create(AdminCourseSaveRequest req) {
        validateSlots(req, null);
        if (!regionMapper.exists(req.regionId())) throw new BusinessException(ErrorCode.COMMON_4040);
        Course course = new Course();
        course.setRegionId(req.regionId());
        course.setName(req.name());
        courseMapper.insert(course);                        // status DRAFT
        insertSlots(course.getCourseId(), req);
        log.info("admin course created courseId={}", course.getCourseId());
        return courseService.getCourseDetail(course.getCourseId(), null, "ko");
        // ※ getCourseDetail 이 ACTIVE 만 조회하면 DRAFT 코스가 404 가 된다 → 관리자용 findByIdAnyStatus 를 쓰도록 STEP 에서 확인
    }

    @Transactional
    public CourseDetailResponse update(Long courseId, AdminCourseSaveRequest req) {
        Course course = courseMapper.findByIdAnyStatus(courseId);
        if (course == null) throw new BusinessException(ErrorCode.COURSE_4041);
        if (courseMapper.countInProgressPilgrimages(courseId) > 0) throw new BusinessException(ErrorCode.COURSE_4092);   // 걷는 중엔 구성 변경 금지
        validateSlots(req, courseId);
        course.setRegionId(req.regionId());
        course.setName(req.name());
        courseMapper.update(course);
        courseSiteMapper.deleteByCourse(courseId);          // 한 트랜잭션. 실패하면 전부 롤백
        insertSlots(courseId, req);
        return courseService.getCourseDetail(courseId, null, "ko");
    }

    @Transactional
    public void changeStatus(Long courseId, StatusChangeRequest req) {
        Course course = courseMapper.findByIdAnyStatus(courseId);
        if (course == null) throw new BusinessException(ErrorCode.COURSE_4041);
        if ("ACTIVE".equals(req.status())) {
            // 5곳 전부 ACTIVE 사찰이어야 사용자에게 열 수 있다. 하나라도 DRAFT 면 싱글페이지가 404 를 낸다
            if (courseSiteMapper.countSites(courseId) != 5 || courseSiteMapper.countInactiveSites(courseId) > 0) {
                throw new BusinessException(ErrorCode.ADMIN_4092);
            }
        }
        courseMapper.updateStatus(courseId, req.status());
        log.info("admin course status courseId={} → {}", courseId, req.status());
    }

    // ── 내부 ──────────────────────────────────────────────────────────────────

    /** @Valid 가 못 잡는 세 조건: position 중복 없음 / verseNo == position / 사찰 중복·타 코스 배정 없음 */
    private void validateSlots(AdminCourseSaveRequest req, Long selfCourseId) {
        Set<Integer> positions = new HashSet<>();
        Set<Long> sites = new HashSet<>();
        for (var slot : req.sites()) {
            if (!positions.add(slot.position())) throw new BusinessException(ErrorCode.COURSE_4042);     // 같은 자리 두 번
            if (!slot.verseNo().equals(slot.position())) throw new BusinessException(ErrorCode.COURSE_4001);   // 구절은 자리 번호와 같아야 한다
            if (!sites.add(slot.siteId())) throw new BusinessException(ErrorCode.COURSE_4093);           // 같은 사찰 두 번
            Long owner = courseSiteMapper.findCourseIdBySite(slot.siteId());                            // 다른 코스가 이미 가진 사찰인가
            if (owner != null && !owner.equals(selfCourseId)) throw new BusinessException(ErrorCode.COURSE_4093);
        }
        // 5곳·1~5 범위는 @Size(min=5,max=5)·@Min/@Max 가 이미 보장. 중복이 없으니 여기 오면 1~5 가 정확히 한 번씩이다
    }

    private void insertSlots(Long courseId, AdminCourseSaveRequest req) {
        for (var slot : req.sites()) {
            courseSiteMapper.insert(courseId, slot.siteId(), slot.position(), slot.verseNo());
        }
    }
}
```

**파일** `src/main/java/com/templestamp/admin/AdminCourseController.java`
```java
// src/main/java/com/templestamp/admin/AdminCourseController.java
package com.templestamp.admin;

import com.templestamp.admin.dto.AdminCourseSaveRequest;
import com.templestamp.admin.dto.SiteDistanceSaveRequest;
import com.templestamp.admin.dto.StatusChangeRequest;
import com.templestamp.course.dto.CourseDetailResponse;
import com.templestamp.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** /api/admin/courses + /api/admin/site-distances — ADMIN */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Validated
@Slf4j
public class AdminCourseController {

    private final AdminCourseService adminCourseService;
    private final AdminSiteDistanceService adminSiteDistanceService;

    @PostMapping("/courses")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CourseDetailResponse> create(@RequestBody @Valid AdminCourseSaveRequest req) {
        return ApiResponse.ok(adminCourseService.create(req));
    }

    @PutMapping("/courses/{courseId}")
    public ApiResponse<CourseDetailResponse> update(@PathVariable @Positive Long courseId,
                                                    @RequestBody @Valid AdminCourseSaveRequest req) {
        return ApiResponse.ok(adminCourseService.update(courseId, req));
    }

    @PatchMapping("/courses/{courseId}/status")
    public ApiResponse<Void> status(@PathVariable @Positive Long courseId,
                                    @RequestBody @Valid StatusChangeRequest req) {
        adminCourseService.changeStatus(courseId, req);
        return ApiResponse.ok();
    }

    /** 이동시간 일괄 등록 — 코스당 20행(5×4, 방향 있음). UPSERT 라 여러 번 보내도 안전 */
    @PutMapping("/site-distances")
    public ApiResponse<Void> siteDistances(@RequestBody @Valid SiteDistanceSaveRequest req) {
        adminSiteDistanceService.upsertAll(req);
        return ApiResponse.ok();
    }
}
```

**파일** `src/main/java/com/templestamp/admin/dto/SiteDistanceSaveRequest.java`
```java
// src/main/java/com/templestamp/admin/dto/SiteDistanceSaveRequest.java
package com.templestamp.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/** PUT /api/admin/site-distances 본문. 한 번에 최대 240행(60곳 기준 전체). siteAId ≠ siteBId 는 DB CHECK + Service 가 함께 검사 */
public record SiteDistanceSaveRequest(
        @NotEmpty @Size(max = 240) List<@Valid Item> items
) {
    public record Item(
            @NotNull @Positive Long siteAId,                 // 직전 사찰
            @NotNull @Positive Long siteBId,                 // 다음 사찰
            @NotNull @Min(1) @Max(1440) Integer minMinutes   // 1분~하루. 이보다 빨리 오면 챕터 5 가 PENDING(TRAVEL_TIME)
    ) {}
}
```

**파일** `src/main/java/com/templestamp/admin/AdminSiteDistanceService.java`
```java
// src/main/java/com/templestamp/admin/AdminSiteDistanceService.java
package com.templestamp.admin;

import com.templestamp.admin.dto.SiteDistanceSaveRequest;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.site.SiteDistanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** site_distance UPSERT. 이 표는 응답 DTO 가 없다(밖으로 나가면 우회 가능 — DTO 배치표 §18). 쓰기만 한다 */
@Service
@RequiredArgsConstructor
public class AdminSiteDistanceService {

    private final SiteDistanceMapper siteDistanceMapper;

    @Transactional
    public void upsertAll(SiteDistanceSaveRequest req) {
        for (var it : req.items()) {
            if (it.siteAId().equals(it.siteBId())) throw new BusinessException(ErrorCode.COMMON_4000);   // 같은 절 → 같은 절
            siteDistanceMapper.upsert(it.siteAId(), it.siteBId(), it.minMinutes());
            // FK 위반(없는 사찰)은 DataIntegrityViolationException → GlobalExceptionHandler 가 409 COMMON-4090 (규약 "그 외 제약 위반 409")
        }
    }
}
```

Mapper XML 추가분(`mapper/course/`·`mapper/site/`):
```xml
<!-- CourseMapper.xml -->
<select id="findByIdAnyStatus" resultType="com.templestamp.course.Course">
  SELECT course_id, region_id, name, description, status, sort_no, created_at, updated_at FROM course WHERE course_id = #{courseId}
</select>
<update id="updateStatus">UPDATE course SET status = #{status} WHERE course_id = #{courseId}</update>
<select id="countInProgressPilgrimages" resultType="int">
  SELECT COUNT(*) FROM pilgrimage WHERE course_id = #{courseId} AND status = 'IN_PROGRESS'
</select>

<!-- CourseSiteMapper.xml -->
<select id="findCourseIdBySite" resultType="long">SELECT course_id FROM course_site WHERE site_id = #{siteId}</select>
<select id="countSites" resultType="int">SELECT COUNT(*) FROM course_site WHERE course_id = #{courseId}</select>
<select id="countInactiveSites" resultType="int">
  SELECT COUNT(*) FROM course_site cs JOIN site s ON s.site_id = cs.site_id
   WHERE cs.course_id = #{courseId} AND s.status &lt;&gt; 'ACTIVE'
</select>
<delete id="deleteByCourse">DELETE FROM course_site WHERE course_id = #{courseId}</delete>
<insert id="insert">
  INSERT INTO course_site (course_id, site_id, position, verse_no) VALUES (#{courseId}, #{siteId}, #{position}, #{verseNo})
</insert>

<!-- RegionMapper.xml -->
<select id="exists" resultType="boolean">SELECT COUNT(*) > 0 FROM region WHERE region_id = #{regionId}</select>

<!-- SiteDistanceMapper.xml -->
<insert id="upsert">
  INSERT INTO site_distance (site_a_id, site_b_id, min_minutes) VALUES (#{siteAId}, #{siteBId}, #{minMinutes})
  ON DUPLICATE KEY UPDATE min_minutes = VALUES(min_minutes)
</insert>
```

**왜 `verseNo == position`을 강제하는가** 스키마 주석이 "= position"이고, 코스 5곳이 오관게 5구를 순서대로 맡는 것이 이 서비스의 구조다. 요청에 verseNo가 따로 있는 건 DTO(79개 확정)를 안 바꾸기 위해서다. 불일치는 `COURSE-4001`("다른 코스의 자리" — 의미상 "자리와 구절이 안 맞음"이 가장 가깝다. 새 코드를 만들지 않는다).

**[담당: Claude Code]**

---

## [기본 39] SecurityConfig `[admin]` 구역 + RegionResponse.code + 부수 파일

```java
// SecurityConfig — [admin] 구역 append (anyRequest 위)
                // [admin]   ROLE_ADMIN 만. 필터가 "ROLE_" + role 로 권한을 만들므로 hasRole("ADMIN") 이 ROLE_ADMIN 을 찾는다
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
```

`RegionRow` 에 `private String code;`, `RegionMapper.xml` SELECT 에 `r.code`, `RegionResponse(regionId, code, name, courseCount)` + `from()` 갱신.

`application-prod.yml` append: `management.endpoints.web.exposure.include: health`

신설 문서 3개(Claude Code 작성): `backend/docs/setup.md`(Java 21·MySQL 8·jq·node·.env.local 키 목록) · `backend/docs/frontend-handoff.md`(의견03 §10 + `/api/regions/` 401 + `/guide` 언어 미수신 + `X-Request-Id` 32자 + `position` 의미 2종) · `backend/docs/deploy-checklist.md`(stamp ALTER · prod include:health · cookie.secure · JWT/QR 시크릿 분리 · `sql.init.mode=never`)

---

## 5. 확인포인트 (curl — 관리자 토큰 필요. `data.sql` 의 `admin@templestamp.local / Admin1234!` 로 로그인)

| # | 요청 | 기대 |
|---|---|---|
| ① | USER 토큰으로 `GET /api/admin/kakao/places?query=통도사` | 403 `AUTH-4032` |
| ② | ADMIN, 키 없음 | 503 `KAKAO-5030` |
| ③ | ADMIN, 키 있음 | 200, `places[0].latitude` 35.4~35.5 |
| ④ | `POST /api/admin/sites` i18n 에 en 만 | 400 `COMMON-4000`, `fields[0].field == "i18n"` |
| ⑤ | 같은 요청에 ko 추가 | 201, `data.siteId` |
| ⑥ | `PATCH .../sites/{id}/status {"status":"ACTIVE"}` qrLocationHint 없이 | 409 `ADMIN-4092` |
| ⑦ | `POST /api/admin/courses` sites 4곳 | 400 `COMMON-4000` |
| ⑧ | sites 5곳 중 site 1(이미 코스 1 소속) 포함 | 409 `COURSE-4093` |
| ⑨ | verseNo≠position | 400 `COURSE-4001` |
| ⑩ | `PUT /api/admin/site-distances` 20행 | 200, `SELECT COUNT(*) FROM site_distance` +20 |
| ⑪ | `GET /api/sites` (옛 공개 검색) | 401 (제거됨) / `GET /api/admin/sites?q=조계` ADMIN → 200 |
| ⑫ | `GET /api/regions` | `items[0].code` 존재 |

---

## 6. Claude Code 지시문 — 확정본 (STEP 0 은 2026-09-05 완료됨. STEP 1 부터)

```
루트 src/ 에서 작업. git 없음. 교재 원문 backend/docs/textbook/ch3.md (이제 있다). 스택 고정(Spring Boot 3.5.16·Java 21·Lombok·MyBatis 3.0.5·MySQL 8·record). ErrorCode 생성자 (status, code, message). 기존 파일 append 우선.
ch3-admin.md §4 답: ① ch3.md 넣었다 ② 키 이름은 저장소 기존 KAKAO_REST_API_KEY 로 통일 — 교재의 KakaoProperties·yml 도 그 이름이다. .env.local 은 손대지 않는다 ③ 관리자 계정은 data.sql 의 admin@templestamp.local / Admin1234! 사용.

STEP 1 — 감사: admin·kakao 패키지 전수를 ch3.md §2 확정 경로표와 대조. 저장소에만 있는 것(place-search 등)·명세에만 있는 것을 표로. CoordinateFieldGuard 화이트리스트 실제 값 확인. BusinessException 에 fields 받는 생성자 유무 확인. → backend/docs/audit/ch3-admin.md 에 이어서 쓴다.

STEP 2 — 카카오: ch3.md [기본 35]·[기본 36] 의 KakaoProperties·KakaoMapConfig·KakaoMapService·AdminKakaoController 를 교재대로. 기존 place-search 는 새 경로 GET /api/admin/kakao/places 로 이동(옛 경로 삭제, 옛 Service 메서드는 [AUDIT] 후 삭제). application.yml kakao 블록은 기존 것을 교재 형태(rest-key·base-url)로 맞춘다. RequiredEnvCheck 대상에 넣지 않는다. 테스트: 키 없이 503 KAKAO-5030 1건, MockRestServiceServer 로 x·y → latitude·longitude 변환 1건.

STEP 3 — 관리자 사찰: [기본 37] 의 DTO 3개(StatusChangeRequest·SiteViewpointSaveRequest·SiteBadgeSaveRequest)·AdminSiteService·AdminSiteController·Mapper XML append. 목록 GET /api/admin/sites 는 기존 공개 검색 로직을 SiteService.searchForAdmin(status 무관)으로 옮겨 연결하고, 공개 GET /api/sites 메서드를 삭제. 저장소의 기존 AdminSiteController/Service 와 diff 를 보고서에 붙이고 교재 규칙(ko 필수·UPSERT·ACTIVE 조건·INACTIVE 조건)이 빠진 곳만 채운다. 기존에 있고 명세에 없는 메서드는 [AUDIT]. 테스트: ko 없음 400 / ACTIVE 조건 409 ADMIN-4092 / i18n 재전송 시 행 수 불변(UPSERT).

STEP 4 — 관리자 코스: [기본 38] 의 AdminCourseService·AdminCourseController·SiteDistanceSaveRequest·AdminSiteDistanceService·Mapper XML append. CourseService.getCourseDetail 이 ACTIVE 만 보면 findByIdAnyStatus 경유 오버로드를 추가. 테스트: 4곳 400 / 중복 사찰 409 COURSE-4093 / verseNo≠position 400 COURSE-4001 / 진행 중 순례 있으면 PUT 409 COURSE-4092 / site-distances UPSERT 멱등.

STEP 5 — [기본 39]: SecurityConfig [admin] 구역 append(anyRequest 위). 공개 GET /api/sites permitAll 줄이 따로 있으면 제거(/api/sites/** 는 유지). ErrorCode 에 COURSE_4001·4042·4092·4093·KAKAO_5030·5031·ADMIN_4092 중 없는 것만 append (status, code, message).

STEP 6 — 실행: ./gradlew test → bootRun(/api/regions 200 대기) → ch3.md §5 확인포인트 12종을 docs/verify/ch3-checkpoints.sh 로 만들어 실행. 관리자 로그인은 admin@templestamp.local / Admin1234!, 본문은 docs/verify/body/*.json UTF-8 --data-binary. ③ 은 .env.local 에 KAKAO_REST_API_KEY 가 있을 때만 실행, 없으면 "미실행(키 없음)" 기록.

STEP 7 — 보고서 backend/docs/audit/ch3-admin.md 갱신: 변경 파일 / 테스트 건수 / 확인포인트 12종 결과 원문 / [질문]. bootRun 종료.
```

**예성 직접 (2가지)**: ① 이 파일을 `backend/docs/textbook/ch3.md`로 저장하고 지시문 붙여넣기 ② 카카오 REST API 키 발급 → `.env.local`에 `KAKAO_REST_API_KEY=` (Chrome 위임: "developers.kakao.com에서 temple-stamp 앱 만들고 REST API 키 복사" — 로그인은 예성이 직접).

다음: **챕터 4 — 순례·여권** (Pilgrimage·Passport, `progress` 엔드포인트 제거)
