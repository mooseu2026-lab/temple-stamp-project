# 챕터 6 — 기록: 업로드 · 사진 · 생각상자 · 여섯 달 전 오늘 · 명상 (F-12 · F-14 · F-15 · F-16 · F-17)

> 작성일 2026-09-06 · 스택 고정: Spring Boot 3.5.16 · Java 21 · Lombok · MyBatis 3.0.5 · MySQL 8 · record · 담당 학생 A(photo·thinkbox) / B(upload·meditation)
> 파일 위치 `backend/docs/textbook/ch6.md`. **교재 정본** — 저장소의 photo·upload·thinkbox·meditation 코드는 검사(교체/삭제/유지) 후 교체, 원본은 `backend/docs/audit/ch6-replaced/`. 보안·동의·권한 검사는 교재에 없어도 유지(정리.md §3 예외 원칙).
> 시작 조건 충족: 중간 점검 B(JUnit 133 · newman 741/741 · DB 10/10). 이 챕터 STEP 0 = 그 보고서 R 폴더의 ⚠ 4건.

---

## 0. 이 챕터가 여는 것 — "도장이 없어도 남는 기록"

챕터 5까지는 **도장**(인증)이 중심이었다. 이 챕터는 도장과 무관하게 순례자가 남기는 **기록** 네 가지다.

| 기록 | 표 | 규칙(v4 · Q1 ②) |
|---|---|---|
| 사진 | `photo` (uk user+site) | **사찰 1곳당 1장.** 도장 없어도 저장. 다시 올리면 교체 |
| 문장 | `thinkbox` (source DIRECT, site_id) | **사찰 1곳당 1개**(site 연결 DIRECT). 자유 기록(site 없음)은 여러 개 |
| 다짐 사본 | `thinkbox` (source MISSION, stamp_id uk) | 챕터 5가 만든 것. 본문 수정 가능 → `is_edited` |
| 명상 기록 | `meditation_log` | 재생 시간·메모. 명상 자체는 비로그인 청취 가능(F-16) |

원칙②(명세 §5.9): **저장과 열람 외의 기능을 넣지 않는다.** 점수·순위·답장 없음. 전자책(챕터 9)이 이 기록들을 그대로 싣는다 — `is_private=1`·`has_other_face=1`은 제외.

---

## 1. 사용자 흐름 ↔ 프로그램 흐름 (명세 흐름 6 · §5.6 · §5.9)

```
[사용자]  사진 고르기 → (앱이 EXIF 제거) → 업로드 → "왜 찍었나" 한 줄 → 저장
             │                                │                      │
[요청]   GET /api/uploads/presign?purpose=PHOTO&contentType=image/jpeg
         → { uploadUrl, fileKey: PHOTO/{userId}/{uuid}.jpg, expiresInSeconds }
             │  앱이 uploadUrl 에 PUT (서버는 바이트를 안 만짐)
             ▼
         PUT /api/photos/{siteId}  { photoKey, sentence?, hasOtherFace, isPrivate }
             │
[Service] 키 소유 검증(PHOTO/{나}/…) → 사찰 ACTIVE → photo UPSERT(uk user+site) → sentence 있으면 thinkbox DIRECT(user,site) UPSERT
             ▼
[응답]    PhotoResponse { siteId, siteName, photoKey, sentence, isPrivate, hasOtherFace, updatedAt }

[생각상자]  POST /api/thinkbox { content, courseId?, siteId?, isPrivate }  ·  GET ?sort=date|course|site&page&size
            PATCH /{id} { content?, isPrivate? } (is_edited 규칙)  ·  DELETE /{id}  ·  GET /api/thinkbox/flashback (6개월 전 오늘, 없으면 data:null)
[명상]      GET /api/meditations?category=&limit=&locale=  ·  GET /{id}  (비로그인 OK)  ·  POST /api/meditations/logs { meditationId, playedSeconds, memo? }
```

**핵심 두 줄**: ① 서버는 사진 바이트를 받지 않는다 — presign 키만 발급하고, 제출 때 그 키가 **내 것인지** 확인한다. ② 사진과 문장은 "사찰당 1개"라 **UPSERT**다. 새 행이 아니라 교체.

---

## 2. 규칙·에러코드

| 규칙 | 코드 |
|---|---|
| presign purpose 가 PHOTO/EVIDENCE 아님 | 400 UPLOAD-4001 |
| contentType 이 image/jpeg·image/png 아님 | 400 COMMON-4000 fields[contentType] |
| photoKey 가 내 접두어(`PHOTO/{userId}/`)가 아니거나 패턴 불일치 | 400 COMMON-4000 fields[photoKey] |
| 사찰이 없거나 ACTIVE 아님 | 404 SITE-4040 |
| sentence 301자 이상 | 400 COMMON-4000 (thinkbox 상한 300 과 동일) |
| 생각상자 남의 글 수정·삭제 | 403 THINKBOX-4031 |
| 생각상자 없음 | 404 THINKBOX-4041 |
| 명상 없음(또는 DRAFT) | 404 MED-4041 |
| flashback 기록 없음 | **200 + data:null** (404 아님, 명세 §4) |

DTO 필드명 정정: `thinkbox.is_private`(DB) ↔ 기존 DTO `isPublic` → **`isPrivate` 로 통일**(반전 실수 방지). 프론트 전달.

---

## [기본 52] 업로드 presign — UploadService

```java
// src/main/java/com/templestamp/upload/UploadService.java
package com.templestamp.upload;

import com.templestamp.global.config.StorageProperties;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.global.type.UploadPurpose;
import com.templestamp.upload.dto.PresignResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

/**
 * 업로드 URL 발급. DB 를 건드리지 않는다(@Transactional 없음).
 * 키 규칙: {PURPOSE}/{userId}/{uuid}.{ext} — 제출 때 "내 접두어인가" 로 소유를 검증한다(PhotoService·StampService).
 * 서버는 바이트를 만지지 않는다. EXIF 제거는 앱 몫(명세 5.6) — 서버는 확인할 수 없으므로 요구만 한다.
 */
@Service
@RequiredArgsConstructor
public class UploadService {

    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png");

    private final ObjectStorageClient storage;     // local: 스텁 URL / prod: SigV4 presign (구현 교체만, 호출부 동일)
    private final StorageProperties props;         // presign 만료(초)

    public PresignResponse presign(Long userId, UploadPurpose purpose, String contentType) {
        if (purpose == null) throw new BusinessException(ErrorCode.UPLOAD_4001);               // enum 변환 실패는 컨트롤러가 COMMON-4003 로 먼저 막고, null 은 여기서
        if (!ALLOWED.contains(contentType)) throw new BusinessException(ErrorCode.COMMON_4000, "contentType 은 image/jpeg 또는 image/png 만 허용합니다.");
        String ext = contentType.equals("image/png") ? "png" : "jpg";
        String key = purpose.name() + "/" + userId + "/" + UUID.randomUUID() + "." + ext;    // PHOTO/12/3f1a…jpg
        String url = storage.presignPut(key, contentType, props.presignSeconds());
        return new PresignResponse(url, key, props.presignSeconds());
    }

    /** 제출 측 공용 검증: 이 사용자의 이 용도 키인가. 패턴은 MissionSubmitRequest 와 같다 */
    public static boolean ownsKey(Long userId, UploadPurpose purpose, String key) {
        return key != null && key.matches("^" + purpose.name() + "/" + userId + "/[0-9a-f-]{36}\\.(jpg|png)$");
    }
}
```

```java
// src/main/java/com/templestamp/upload/UploadController.java
package com.templestamp.upload;

import com.templestamp.global.response.ApiResponse;
import com.templestamp.global.security.AuthenticatedUser;
import com.templestamp.global.type.UploadPurpose;
import com.templestamp.upload.dto.PresignResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** GET /api/uploads/presign?purpose=PHOTO&contentType=image/jpeg — 로그인 필요 */
@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
@Validated
public class UploadController {
    private final UploadService uploadService;

    @GetMapping("/presign")
    public ApiResponse<PresignResponse> presign(@RequestParam UploadPurpose purpose,          // PHOTO | EVIDENCE. 다른 값은 400 COMMON-4003
                                                @RequestParam @NotBlank String contentType,
                                                @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(uploadService.presign(user.userId(), purpose, contentType));
    }
}
```

**[담당: Claude Code]** · `MissionSubmitRequest`의 photoKey 패턴은 `.jpg` 만 허용 중 — `(jpg|png)` 로 넓힌다(챕터 5 회귀 주의).

---

## [기본 53] 사진 + 문장 — PhotoService (Q1 ②)

```java
// src/main/java/com/templestamp/photo/dto/PhotoSaveRequest.java
package com.templestamp.photo.dto;
import jakarta.validation.constraints.*;

/** PUT /api/photos/{siteId} 본문. 사찰당 1장·문장 1개 — 다시 보내면 교체(UPSERT). memo 필수(F-12)는 sentence 로 통일 */
public record PhotoSaveRequest(
        @NotBlank @Pattern(regexp = "^PHOTO/\\d+/[0-9a-f-]{36}\\.(jpg|png)$", message = "사진 키 형식이 올바르지 않습니다.") String photoKey,
        @NotBlank @Size(max = 300, message = "문장은 300자 이하로 입력해주세요.") String sentence,   // "왜 찍었는지 한 줄" — F-12 memo 필수
        boolean hasOtherFace,           // 타인 얼굴 포함(자기 신고) → 전자책 제외
        boolean isPrivate               // 비공개 → 전자책 제외
) {}

// src/main/java/com/templestamp/photo/dto/PhotoResponse.java
package com.templestamp.photo.dto;
import java.time.LocalDateTime;
public record PhotoResponse(Long siteId, String siteName, String photoKey, String sentence,
                            boolean hasOtherFace, boolean isPrivate, LocalDateTime updatedAt) {}
```

```java
// src/main/java/com/templestamp/photo/PhotoService.java
package com.templestamp.photo;

import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.global.type.UploadPurpose;
import com.templestamp.photo.dto.PhotoResponse;
import com.templestamp.photo.dto.PhotoSaveRequest;
import com.templestamp.site.Site;
import com.templestamp.site.SiteMapper;
import com.templestamp.thinkbox.ThinkboxService;
import com.templestamp.upload.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 사찰당 사진 1장 + 문장 1개(개인 소장). 도장 여부를 묻지 않는다 — Q1 ② 확정.
 * 한 트랜잭션: photo UPSERT + thinkbox DIRECT UPSERT. 둘 중 하나가 실패하면 둘 다 없던 일.
 */
@Service
@RequiredArgsConstructor
public class PhotoService {

    private final PhotoMapper photoMapper;
    private final SiteMapper siteMapper;
    private final ThinkboxService thinkboxService;

    @Transactional
    public PhotoResponse save(Long userId, Long siteId, PhotoSaveRequest req) {
        if (!UploadService.ownsKey(userId, UploadPurpose.PHOTO, req.photoKey()))            // 남의 키·경로 조작 차단
            throw new BusinessException(ErrorCode.COMMON_4000, "본인이 발급받은 사진 키가 아닙니다.");
        Site site = siteMapper.findActiveById(siteId);
        if (site == null) throw new BusinessException(ErrorCode.SITE_4040);

        photoMapper.upsert(userId, siteId, req.photoKey(), req.hasOtherFace(), req.isPrivate());   // uk_photo_user_site
        thinkboxService.upsertDirectForSite(userId, siteId, site.getCourseIdOrNull(), req.sentence(), req.isPrivate());   // 사찰당 문장 1개
        return get(userId, siteId);
    }

    @Transactional(readOnly = true)
    public PhotoResponse get(Long userId, Long siteId) {
        var row = photoMapper.findRow(userId, siteId);                                        // photo JOIN site_i18n(ko) LEFT JOIN thinkbox DIRECT
        if (row == null) throw new BusinessException(ErrorCode.COMMON_4040);
        return new PhotoResponse(row.getSiteId(), row.getSiteName(), row.getPhotoKey(), row.getSentence(),
                row.getHasOtherFace(), row.getIsPrivate(), row.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public List<PhotoResponse> listMine(Long userId) { return photoMapper.findAllRows(userId).stream().map(r ->
            new PhotoResponse(r.getSiteId(), r.getSiteName(), r.getPhotoKey(), r.getSentence(), r.getHasOtherFace(), r.getIsPrivate(), r.getUpdatedAt())).toList(); }
}
```

```xml
<!-- src/main/resources/mapper/photo/PhotoMapper.xml (핵심) -->
<insert id="upsert">
  INSERT INTO photo (user_id, site_id, file_key, has_other_face, is_private)
  VALUES (#{userId}, #{siteId}, #{fileKey}, #{hasOtherFace}, #{isPrivate})
  ON DUPLICATE KEY UPDATE file_key = VALUES(file_key), has_other_face = VALUES(has_other_face), is_private = VALUES(is_private)
  <!-- uk_photo_user_site — 같은 절에 다시 올리면 교체. 옛 파일 키는 덮이므로 스토리지 정리는 배치(챕터 9 이후) -->
</insert>
<select id="findRow" resultType="com.templestamp.photo.dto.PhotoRow">
  SELECT p.site_id AS siteId, COALESCE(si.name, s.name) AS siteName, p.file_key AS photoKey,
         t.body AS sentence, p.has_other_face AS hasOtherFace, p.is_private AS isPrivate, p.updated_at AS updatedAt
    FROM photo p
    JOIN site s ON s.site_id = p.site_id
    LEFT JOIN site_i18n si ON si.site_id = s.site_id AND si.locale = 'ko'
    LEFT JOIN thinkbox t ON t.user_id = p.user_id AND t.site_id = p.site_id AND t.source = 'DIRECT'   <!-- ON 에 둔다 — 문장 없는 사진도 나와야 한다 -->
   WHERE p.user_id = #{userId} AND p.site_id = #{siteId}
</select>
```

```java
// src/main/java/com/templestamp/photo/PhotoController.java
@RestController @RequestMapping("/api/photos") @RequiredArgsConstructor @Validated
public class PhotoController {
    private final PhotoService photoService;

    @PutMapping("/{siteId}")                                                                    // 있으면 교체, 없으면 생성 → 항상 200
    public ApiResponse<PhotoResponse> save(@PathVariable @Positive Long siteId, @RequestBody @Valid PhotoSaveRequest req,
                                           @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(photoService.save(user.userId(), siteId, req));
    }
    @GetMapping("/{siteId}")
    public ApiResponse<PhotoResponse> get(@PathVariable @Positive Long siteId, @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(photoService.get(user.userId(), siteId));
    }
    @GetMapping                                                                                 // 내 사진 전부(전자책 미리보기용)
    public ApiResponse<ItemsResponse<PhotoResponse>> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(ItemsResponse.of(photoService.listMine(user.userId())));
    }
}
```

**[담당: Claude Code]** — 도장의 사진(`stamp.photo_key`)과 이 표의 사진은 **별개**다. 도장 사진은 다짐 제출 때 붙는 것이고, 이 표는 개인 소장이다. 전자책은 둘 다 싣는다.

---

## [기본 54] 생각상자 — ThinkboxService (is_edited · 사찰당 DIRECT 1개 · flashback)

```java
// src/main/java/com/templestamp/thinkbox/ThinkboxService.java (핵심 메서드)
/** 사찰에 묶인 DIRECT 기록은 1개 — 있으면 본문·공개여부 갱신(is_edited 규칙), 없으면 생성. PhotoService 가 호출 */
@Transactional
public void upsertDirectForSite(Long userId, Long siteId, Long courseId, String content, boolean isPrivate) {
    Thinkbox t = thinkboxMapper.findDirectBySite(userId, siteId);
    if (t == null) thinkboxMapper.insert(userId, content, "DIRECT", null, courseId, siteId, isPrivate);
    else thinkboxMapper.update(t.getThinkboxId(), content, isPrivate);      // XML: is_edited 대입이 body 보다 먼저(정리.md §6-1)
}

/** 챕터 5 가 부른다 — 미션 다짐의 수정 가능한 사본. uk_thinkbox_stamp 로 도장당 1개 */
@Transactional
public void createFromMission(Long userId, Long stampId, String sentence, Long courseId, Long siteId) {
    thinkboxMapper.insertIgnore(userId, sentence, "MISSION", stampId, courseId, siteId, false);
}

/** 여섯 달 전 오늘 — 없는 게 정상이라 null 을 그대로 돌려준다(컨트롤러가 200 + data:null) */
@Transactional(readOnly = true)
public ThinkboxResponse flashback(Long userId) {
    ThinkboxRow r = thinkboxMapper.findFlashback(userId);
    return r == null ? null : ThinkboxResponse.from(r);
}
```

```xml
<!-- ThinkboxMapper.xml -->
<update id="update">
  UPDATE thinkbox
     SET is_edited  = CASE WHEN body <> #{body} THEN 1 ELSE is_edited END,   <!-- ★ 읽는 쪽이 먼저 — MySQL SET 은 왼쪽부터 평가 -->
         body       = #{body},
         is_private = #{isPrivate}
   WHERE thinkbox_id = #{thinkboxId}
</update>
<select id="findDirectBySite" resultType="com.templestamp.thinkbox.Thinkbox">
  SELECT * FROM thinkbox WHERE user_id = #{userId} AND site_id = #{siteId} AND source = 'DIRECT' LIMIT 1
</select>
<!-- 여섯 달 전 오늘: 서버 시간대 기준 날짜 일치. 여러 건이면 가장 이른 것 -->
<select id="findFlashback" resultType="com.templestamp.thinkbox.dto.ThinkboxRow">
  SELECT t.thinkbox_id AS thinkboxId, t.body AS content, t.source, t.is_private AS isPrivate, t.is_edited AS isEdited,
         t.course_id AS courseId, c.name AS courseName, t.site_id AS siteId, COALESCE(si.name, s.name) AS siteName, t.created_at AS createdAt
    FROM thinkbox t
    LEFT JOIN course c ON c.course_id = t.course_id
    LEFT JOIN site s ON s.site_id = t.site_id
    LEFT JOIN site_i18n si ON si.site_id = s.site_id AND si.locale = 'ko'
   WHERE t.user_id = #{userId} AND DATE(t.created_at) = CURDATE() - INTERVAL 6 MONTH
   ORDER BY t.created_at LIMIT 1
</select>
```

컨트롤러: `POST /api/thinkbox`(201) · `GET ?sort=date|course|site&page&size`(PageResponse, sort 화이트리스트 — 그 외 400) · `PATCH /{id}` · `DELETE /{id}`(소유자만, 403 THINKBOX-4031) · **`GET /api/thinkbox/flashback`** — `/{id}` 보다 **위에** 선언해야 "flashback"이 id 로 잡히지 않는다(@Validated @Positive 라 400이 됨).

**[담당: Claude Code]**

---

## [기본 55] 명상 — MeditationService

- 목록·상세는 **ACTIVE 만**, 비로그인 허용(SecurityConfig `GET /api/meditations/**` permitAll). `locale` 은 `meditation_i18n` COALESCE(ko 폴백), 응답에 실제 나간 `lang`.
- 목록 `?category=1..10&limit=1..50&locale=` → `MeditationListResponse{items, categoryCounts}`. 스크립트는 목록에서 제외(경량).
- 상세 `audioUrl` = `storage.presignGet(audio_key)` — 저장소 미구성(local)이면 null.
- 기록 `POST /api/meditations/logs { meditationId, playedSeconds ≥0, memo ≤300 }` → 201. 명상이 DRAFT/없음이면 404 MED-4041. **기록은 저장만** — 통계·추천 없음(원칙②).

**[담당: Claude Code]** — 저장소 코드가 이 규칙과 같으면 유지, 다르면 교체. i18n 폴백 규칙은 content/i18n 과 동일하게(정리.md §4-6).

---

## 5. 확인포인트 (컬렉션 폴더 V — 로그인 사용자 `reg-*`, 사찰은 데모 코스의 ACTIVE 사찰)

| # | 요청 | 기대 |
|---|---|---|
| V01 | `GET /api/uploads/presign?purpose=PHOTO&contentType=image/jpeg` | 200, fileKey `PHOTO/{userId}/…jpg` → 환경 `photoKey` |
| V02 | `purpose=X` | 400 COMMON-4003 |
| V03 | `contentType=image/gif` | 400 COMMON-4000 fields[contentType] |
| V04 | `PUT /api/photos/{site1} {photoKey, sentence:"첫 사진", hasOtherFace:false, isPrivate:false}` | 200, sentence 반영 |
| V05 | 같은 사찰에 다시 PUT(다른 키·문장 "고친 문장") | 200 · SQL photo 1행 · thinkbox DIRECT 1행 · is_edited=1 |
| V06 | 남의 키(`PHOTO/999/…`) | 400 COMMON-4000 fields[photoKey] |
| V07 | DRAFT 사찰 | 404 SITE-4040 |
| V08 | `GET /api/photos` | items 1 |
| V09 | `POST /api/thinkbox {content:"자유 기록", isPrivate:true}` | 201 |
| V10 | `GET /api/thinkbox?sort=date` | items ≥2(DIRECT 사찰 1 + 자유 1) |
| V11 | `?sort=hack` | 400 |
| V12 | `PATCH /{id} {isPrivate:false}`(본문 그대로) | 200, isEdited **false** 유지 |
| V13 | 다른 계정으로 PATCH | 403 THINKBOX-4031 |
| V14 | `GET /api/thinkbox/flashback` | 200, data null |
| V15 | (SQL로 created_at 을 6개월 전으로 UPDATE) → flashback | 200, data 있음 |
| V16 | `GET /api/meditations`(비로그인) | 200, items ≥1, DRAFT 없음 |
| V17 | `GET /api/meditations/{id}?locale=en` | lang 필드(en 없으면 ko) |
| V18 | `POST /api/meditations/logs {meditationId, playedSeconds:120}` | 201 |
| V19 | DRAFT 명상 id 로 logs | 404 MED-4041 |
| — | 챕터 5 회귀: T10 photoKey 를 `.png` 로 | 200 |

cleanup.sql: photo·thinkbox(reg-*)·meditation_log 추가.

---

## 6. Claude Code 지시문 — 확정본

```
루트 src/ 작업. git 없음. 원문 backend/docs/textbook/ch6.md. 스택 고정. ErrorCode (status, code, message). 교재 정본(원본 backend/docs/audit/ch6-replaced/). 보안·동의·권한 검사는 교재에 없어도 유지.

STEP 0 — 중간 점검 B 의 R 폴더 ⚠ 4건(feature-status-ch0-5.md "챕터 6~9 착수 시 수정" 표)을 먼저 처리. 이 챕터 범위(photo·upload·thinkbox·meditation) 밖의 것은 목록만 옮겨 적고 손대지 않는다.

STEP 1 — 검사표: photo·upload·thinkbox·meditation 패키지·XML·테스트 전수 → 근거/교재와의 차이/판정(교체·삭제·유지). 특히: presign 키 규칙과 소유 검증 위치, photo UPSERT 여부, thinkbox isPublic↔is_private 반전 여부, is_edited SET 순서, flashback 존재 여부와 경로 선언 순서, 명상 ACTIVE 필터·i18n 폴백, DTO 79개 확정본과의 차이. → docs/audit/ch6-record.md §1.

STEP 2 — 교체: [기본 52]~[기본 55]. DTO 변경: ThinkboxCreate/UpdateRequest·Row·Response 의 isPublic → isPrivate(정리.md §4 프론트 전달 추가), PhotoSaveRequest·PhotoResponse·PhotoRow 신설, MissionSubmitRequest photoKey 패턴 (jpg|png). SecurityConfig: GET /api/meditations/** permitAll 확인. 에러코드 UPLOAD-4001·THINKBOX-4031·4041·MED-4041 없는 것만 append(명세 §7 근거).

STEP 3 — 테스트(RecordIntegrationTest): presign 키 규칙·소유 검증 / 사진 UPSERT 1행 / 문장 UPSERT + is_edited / 남의 키 400 / DRAFT 사찰 404 / 생각상자 CRUD·403 / flashback null·6개월 전 / 명상 DRAFT 제외·i18n 폴백·logs 201. 목표 총 150건 이상.

STEP 4 — 컬렉션 폴더 "V 기록" V01~V19 를 T 다음에 append. cleanup.sql 갱신. run-all.sh 2회 동일.

STEP 5 — 보고서 docs/audit/ch6-record.md: STEP 0 처리표 / 검사표 / 교체·삭제·유지 / 테스트 건수 / newman / [질문]. 정리.md §1 표(photo·upload·thinkbox·meditation "완료·종단 테스트 있음")·§4(isPrivate·PUT /api/photos·flashback 200 null·presign png 허용). 마지막 줄 형식 동일. bootRun 종료. 챕터 7 은 시작하지 않는다.
```

**예성 직접**: `ch6.md` → `backend/docs/textbook/` → §6 붙여넣기 → 마지막 줄 확인.
다음: **챕터 7 — 완주 재집계 · 인증서 · 보상**(CompletionService·RewardService·CertificateService, claimable 서버 재확인).
