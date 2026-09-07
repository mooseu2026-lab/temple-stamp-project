# 챕터 6 — 기록: 사진 · 문장 · 생각상자 · 명상

원문 `backend/docs/textbook/ch6.md` · 루트 `src/` · git 없음 · 교재 정본(원본 `backend/docs/audit/ch6-replaced/`)
시작 상태: JUnit 133 · newman 요청 168 / 단언 741 · 실패 0

```
JUnit   151/151   (133 → +18)
newman  요청 192/192 · 단언 862/862 · 실패 0     ← 2회 연속 같은 값
SQL     채점 11절 전부 기대값 (신규 ⑪ photo 1 · direct 1 · is_edited 1)
컬렉션  A16 U10 P10 T31 V24 R30 M18 G6 K4 S·C20
```

---

## 0. 중간 점검 B 의 ⚠ 4건 — 이 챕터 범위 밖이라 목록만 옮긴다

| # | 무엇 | 어느 챕터 | 이번에 한 것 |
|---|---|---|---|
| 5-1 | 보상 수령 신청이 종류를 가리지 않는다 (`claim` 이 `claimable:false` 도 200) | **7** | 손대지 않음. 조건은 확정돼 있다 — `PHYSICAL` + `GRANTED` + 본인 소유, 아니면 400 `REWARD-4001` / 409 `REWARD-4092` |
| 5-2 | 인증서·전자책·인쇄가 빈 상태로만 확인됨 (`GET/DELETE /api/print-orders/{id}` 미실행) | **9** | 손대지 않음 |
| 5-4 | 확장문구 `versionNo` 는 1~5, 미션 `variantNo` 는 상한 없음 | **8** | 손대지 않음 |
| 5-5 | 인쇄 주문 상태에 `PRINTING` 이 없다 | **9** | 손대지 않음 |

이 챕터 범위(photo·upload·thinkbox·meditation)에 걸린 것은 없었다.

---

## 1. 검사표

| 파일 | 근거 | 교재와의 차이 | 판정 |
|---|---|---|---|
| `UploadService` | [기본 52] | `contentType` 을 아예 받지 않았다. 키 확장자가 **언제나 `.jpg`** 고, 허용 형식 검사도 소유 검증 헬퍼도 없다 | **교체** — `contentType` 파라미터 · `image/jpeg\|png` 허용 목록 · `ownsKey` 추가 |
| `UploadController` | [기본 52] | `purpose` 만 받는다 | **교체** — `@RequestParam @NotBlank String contentType` |
| `ObjectStorageClient` | — | `createFileKey` 가 `.jpg` 고정 | **유지 + 확장** — `createFileKey(purpose, userId, ext)` 오버로드. `verifyOwnedKey`(경로 조작·`..` 차단)는 그대로 둔다 |
| `PhotoService` | [기본 53] | `register(userId, siteId, fileKey, hasOtherFace)` 하나뿐. 문장·조회·목록이 없다 | **교체 + 유지** — `save`·`get`·`listMine` 신설, 도장 경로가 쓰는 `register` 는 남긴다 |
| `PhotoMapper`(+XML) | [기본 53] | `upsert` 가 **`is_private` 를 갱신하지 않는다**. 문장을 붙여 읽는 질의가 없다 | **교체** — `is_private` 를 UPDATE 목록에 넣고 `findRow`·`findAllRows` 추가 |
| `PhotoController` · `PhotoSaveRequest` · `PhotoResponse` · `PhotoRow` | [기본 53] | **없다** | **신설** |
| `ThinkboxCreate/UpdateRequest` · `Response` · `Row` | [기본 54] | DTO 만 `isPublic`(반대 뜻), DB 는 `is_private` | **교체** — 전부 `isPrivate` 로 (§3) |
| `ThinkboxService` | [기본 54] | `upsertDirectForSite`·`flashback` 없음. 목록에 정렬 없음 | **교체** |
| `ThinkboxMapper`(+XML) | [기본 54] | `findDirectBySite`·`findFlashback` 없음. `NOT t.is_private AS is_public` 별칭 | **교체** |
| `ThinkboxMapper.xml` `update` 의 `SET` 순서 | 정리.md §6-1 | **이미 올바르다** — `is_edited` 대입이 `body` 보다 앞 | **유지** ✅ |
| `ThinkboxController` | [기본 54] | `sort` 없음, `flashback` 없음 | **교체** — `@Pattern(date\|course\|site)` · `/flashback` 을 위에 선언 |
| `MeditationService` | [기본 55] | 상세는 이미 `ACTIVE` 만(`filter(Meditation::isActive)`) · i18n 폴백도 있다 | **유지 + 보완** — 어느 언어가 나갔는지 응답에 담지 않았다 → `lang` 추가 |
| `MeditationMapper.xml` | [기본 55] | 목록·집계는 `status = 'ACTIVE'` | **유지** ✅ |
| `SecurityConfig` | [기본 55] | `GET /api/meditations/**` permitAll | **유지** ✅ |
| `MissionSubmitRequest` · `EvidenceRequest` | [기본 52] | `photoKey` 패턴이 `.jpg` 만 | **교체** — `(jpg\|png)`. png 를 허용하면서 이쪽을 빼면 도장 사진이 막힌다 |
| `ErrorCode` | [기본 52~55] | 아래 §2 | **추가 없음** |

원본 14개는 `backend/docs/audit/ch6-replaced/` 에 있다.

---

## 2. 에러코드 — 새로 만들지 않았다

교재는 `UPLOAD-4001`·`THINKBOX-4031`·`THINKBOX-4041`·`MED-4041` 을 든다.
**뜻이 같은 코드가 이미 있어 번호만 다르다.** 같은 뜻에 두 코드를 두면 프론트가 둘 다 분기해야 하므로 기존 것을 쓴다.

| 교재의 이름 | 실제 상수 | |
|---|---|---|
| `UPLOAD-4001` | `UPLOAD_4001` (400) | 그대로 있다 ✅ |
| `THINKBOX-4031` | **`THINKBOX_4030`** (403 "본인의 글이 아닙니다.") | 번호만 다름 |
| `THINKBOX-4041` | **`THINKBOX_4040`** (404) | 〃 |
| `MED-4041` | **`MEDITATION_4040`** (404) | 〃 |

`contentType` 오류는 `COMMON-4000` + `fields[contentType]` 으로 낸다(교재와 같다).
`photoKey` 소유 불일치도 `COMMON-4000` + `fields[photoKey]` 다 — 폼 오류로 보여야 하는 값이라서다.

---

## 3. `isPublic` → `isPrivate` — 이름 하나가 뜻을 뒤집는다

DB 컬럼은 `thinkbox.is_private` 인데 DTO 만 `isPublic`(반대 뜻)이었다.
**반대 뜻의 이름이 둘 있으면 어디선가 한 번만 뒤집기를 빠뜨려도 조용히 반대로 저장된다.**

바꾼 곳 — `ThinkboxCreateRequest`(신설) · `ThinkboxUpdateRequest` · `ThinkboxResponse` · `ThinkboxRow` ·
`ThinkboxService.create/update` · XML 별칭(`NOT t.is_private AS is_public` → `t.is_private AS is_private`).

> **실제로 걸렸다.** 컬렉션 R03 의 본문을 `isPublic:false` → `isPrivate:false` 로 **값만 그대로 옮겼더니 403** 이 났다.
> `isPublic:false`(비공개 유지)와 `isPrivate:false`(공개로 전환)는 정반대이고, 공개 전환은 전자책 수록 동의를 요구하기 때문이다.
> 이름을 바꾸는 김에 값도 함께 뒤집어야 한다는 것이 그 자리에서 드러났다 — `isPrivate:true` 로 고쳤다.
> 이런 종류는 컴파일러가 잡아 주지 않는다. 이름을 반대로 두면 안 되는 이유가 이것이다.

**프론트 전달** — 요청·응답 모두 `isPrivate` 다. `true` 가 비공개다(정리.md §4-11).

---

## 4. 사진과 문장은 한 트랜잭션

`PUT /api/photos/{siteId}` 하나가 `photo` 와 `thinkbox`(DIRECT) 두 표를 함께 쓴다.

- 서버는 **사진 바이트를 만지지 않는다.** presign 으로 키만 내주고, 제출된 키가 `PHOTO/{내번호}/…` 인지만 본다.
- **사찰당 하나**라 새 행이 아니라 교체다. 다시 저장하면 사진 키·공개 여부가 갈리고,
  문장이 실제로 바뀌었을 때만 `is_edited` 가 켜진다(SET 순서 규칙).
- `findRow` 의 `LEFT JOIN thinkbox` 조건은 **`ON` 에 둔다.** `WHERE` 로 내리면 문장이 아직 없는 사진이
  통째로 사라진다(정리.md §6-3).
- 사찰이 어느 코스의 자리인지는 `courseSiteMapper.findCourseIdBySite` 로 채운다.
  교재의 `site.getCourseIdOrNull()` 은 저장소에 없다 — `site` 는 코스를 모른다.

도장의 사진(`stamp.photo_key`)과 이 표의 사진은 **별개**다. 전자책은 둘 다 싣고 `isPrivate`·`hasOtherFace` 는 뺀다.

---

## 5. 테스트 (151건, 133 → +18)

`RecordIntegrationTest` 18건

| 묶음 | 무엇 |
|---|---|
| presign 4 | 내 번호가 박힌 키 · png 확장자 · `purpose` 밖 400 · gif 400 `fields[contentType]` |
| 사진+문장 6 | 저장 시 두 표 1행씩 · 재저장 시 교체 + `is_edited` · 남의 키 400 · DRAFT 사찰 404 · 301자 400 · 목록/단건 |
| 생각상자 5 | CRUD · 정렬 값 밖 400 · 남의 글 403(수정·삭제) · flashback null · 6개월 전 글 |
| 명상 3 | ACTIVE 만(내렸다 되돌려 확인) · `lang` 폴백 · 기록 저장/404 |

컬렉션 **`V 기록 (챕터 6)`** 24요청을 `T` 다음에 넣었다. `cleanup.sql` 에 `photo` 정리,
`all-checkpoints.sql` 에 ⑪(사진 1행·문장 1행·`is_edited` 1) 추가.

---

## 6. [질문] — 2026-09-06 답 반영

| # | 질의 | 답 | 이번에 한 것 |
|---|---|---|---|
| Q-a | 관리자 사찰 목록에 `status` 가 없다 | **지금 고친다** | **반영 완료** — 아래 §6-1 |
| Q-b | 교체된 옛 사진 파일이 저장소에 남는다 | 교체 시점에 **삭제 큐**로 | **[챕터 9 반영]** 표에 기록만 — §6-2 |
| Q-c | `flashback` 의 기준 시각 | 하루 한도와 **같은 문제**로 확정 | 정리.md §5-2 9번에 "flashback 포함" 한 줄. **코드 변경 없음** |
| Q-d | 사진 없이 문장만 남기고 싶을 때 | **의도한 동작** — 사찰당 DIRECT 문장은 자리 1개, 나중 것이 앞 것을 덮는다 | 정리.md §4-11 에 한 줄. **코드 변경 없음** |

### 6-1. 관리자 목록에 `status` — 반영 완료

| 바뀐 곳 | 무엇 |
|---|---|
| `admin/dto/AdminSiteListResponse` (신설) | `siteId·name·**status**·latitude·longitude·verifyRadius·qrLocationHint` |
| `SiteService.searchForAdmin` | 반환형이 `SiteResponse` → `AdminSiteListResponse`. `status` 인자 추가 |
| `SiteMapper.searchAnyStatus` · `countSearchAnyStatus` (+XML) | `status` 조건 추가(null 이면 전부) |
| `AdminSiteController` | `?status=DRAFT\|ACTIVE\|INACTIVE`(선택). 그 밖의 값은 400 |
| 컬렉션 | V06b 를 `?status=DRAFT` 로. S09 에 "목록에 status 필드가 있다" 단언 추가 |

**공개 DTO 를 건드리지 않은 이유** — `SiteResponse` 에 `status` 를 넣으면 `GET /api/sites/{id}` 에도
따라 나간다. 공개 경로는 ACTIVE 만 내보내므로 그 필드가 필요 없고, 없는 편이 계약이 좁다.

### 6-2. [챕터 9 반영] — 교체된 옛 사진 파일 정리

사진을 다시 올리면 `photo.file_key` 는 갈리지만 저장소의 옛 객체는 남는다. 확정된 방식은 이렇다.

| 항목 | 내용 |
|---|---|
| 새 표 | `storage_orphan(file_key, enqueued_at, deleted_at NULL)` |
| 넣는 시점 | 사진 교체 시 옛 키를 큐에 넣는다 |
| 지우는 쪽 | **5분 청소기**(`StampExpireService.expireStale` 과 같은 방식) 가 스토리지에서 지운 뒤 `deleted_at` 기록 |
| 실제 삭제 | `ObjectStorageClient.delete` — **local 프로파일은 no-op** |
| 언제 | **챕터 9 착수 전.** 이번에는 기록만 한다 |

큐를 두는 이유는 삭제가 실패해도 사용자의 저장이 실패하면 안 되기 때문이다 —
스토리지가 잠깐 죽어도 사진 교체는 성공해야 하고, 못 지운 것은 다음 청소기가 다시 시도한다.

---

**newman 862/862 · 기능 ✅ 7 · ⚠ 0 · ❌ 0 · — 1**

- ✅ presign(형식·확장자·소유) · 사진+문장 한 트랜잭션 · `isPrivate` 통일 · 생각상자 정렬/flashback ·
  명상 `lang` · png 허용 · **관리자 목록 `status`·필터**
- ⚠ 없음
- — 옛 사진 파일 정리(`storage_orphan` + 5분 청소기)는 **챕터 9 착수 전** 반영 — §6-2
