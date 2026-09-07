# 챕터 11-A — FE 준비 정정 · 사진 저장 · 전 코스 ACTIVE · 3조건 검증

2026-09-07 · 절대 규칙 적용(보안·동의·권한 제거 금지 / ErrorCode 순서·번호 고정 / 엔드포인트 신설은 정본이 명시한 1개만 / 좌표 미수신 / 잠금은 있는 행에만 / 일괄 UPDATE 는 PK 순 200건 배치 / 파일은 바이트를 읽어야 ✅ / 설정 착오는 기동 실패)

> **전제 문서** — `frontend-design-v2.1.md` 는 저장소에 **없다.** 들어온 것은 `frontend-design-v2.md` 이고
> 지시문이 말한 §7-4·§14-7·§18 의 ⛔ 상자가 그 문서에는 없다. 대신 **§21 "이 설계서가 요구하는 백엔드 정정"**
> 표(B-1~B-5)가 ⛔ 항목과 대응하므로 그것을 정본으로 삼았다.
>
> | v2 §21 | 대응 |
> |---|---|
> | B-1 refresh 쿠키 `Secure` — local false / prod true | (아래 STEP 2 부록) |
> | B-2 `cors.allowed-origins` 에 `https://192.168.*.*:5173` | **11-B** |
> | B-3 QR 내용 `${FRONTEND_URL}/checkin?token=…` | **STEP 3** |
> | B-4 `by-slot` 없을 때 200+null 인지 404 인지 | STEP 8 에서 실측 |
> | B-5 local `JWT_ACCESS_TTL` | **STEP 2** |

---

## STEP 0 — 출발선

`all.sh` 6단계 전부 통과(newman 1·2회차 · 2회 바이트 동일 · DB H1~H18 어긋남 0 · 보안 **86/0** · 교착 0 · 5xx 0).
`git status` 깨끗 · HEAD `57aac91`(C1).

---

## STEP 1 — 생성기 (⛔3·⛔6)

### 무엇이 잘못돼 있었나

```js
// 고치기 전 — match 는 첫 하나만 잡는다
const m = noComments.match(/public record (\w+)\s*\(([\s\S]*?)\)\s*\{/);
if (!m) continue;
```

`match` 는 파일에서 **처음 만나는 record 하나**만 가져온다. 그래서 중첩 record 가 통째로 빠졌다 —
`SitePageResponse` 는 안에 record 가 **9개**, `PassportResponse` 는 **5개**인데 각각 하나만 나왔다.
결과물에는 **선언 없이 참조되는 이름**이 남았고, 그 파일은 `tsc` 가 거절하는 파일이었다.

### 고친 셋

| # | 무엇 | 어떻게 |
|---|---|---|
| 1 | 중첩 record | `match` → **`matchAll`**. 이름이 겹치는 중첩(`Counts`·`Item` 각 2곳)은 바깥 이름을 앞에 붙여 가른다 |
| 2 | Lombok `@Getter` 조회 DTO | record 가 아니라 필드 선언이라 정규식에 안 걸렸다. `private ... name;` 을 읽는 갈래를 더했다 — `EbookStampRow`·`EbookPhotoRow` 같은 이름이 그래서 미선언으로 남아 있었다 |
| 3 | `error.fields` | `Record<string, string> \| null` 로 **틀리게** 적혀 있었다. 실제는 `List<FieldError>` 이고 `FieldError {field, reason}` 다 |

### 하나 더 — `.d.ts` 는 값을 담을 수 없다

`tsc` 를 처음 돌리자 이렇게 막혔다.

```
api-types.d.ts(1893,47): error TS1039: Initializers are not allowed in ambient contexts.
```

`export const ENDPOINTS = [...]` 가 문제였다. `.d.ts` 는 **선언만** 담는 파일이라 초기값을 쓸 수 없다.
타입(`Endpoint`)은 `.d.ts` 에 두고 **값 목록은 `api-endpoints.ts`** 로 뺐다. 둘 다 생성기가 낸다.

### 결과

```
DTO 182 · enum 8 · 엔드포인트 91 → backend/docs/frontend/api-types.d.ts
중괄호 균형 0 · 타입을 못 정한 필드 0 · 모양이 이상한 줄 0

선언된 타입 203 · 참조된 타입 48 · 미선언 타입 0
tsc --noEmit  종료코드 0        (strict: true · skipLibCheck: false)
```

**DTO 104 → 182.** 정본은 "개수 단언 91·104 유지" 라고 했지만, 중첩 record 를 펴는 것이 이번 수정의
목적이므로 **104 는 늘어나는 것이 맞다**. 엔드포인트 **91 은 그대로다.**

| 확인 | 결과 |
|---|---|
| 미선언 타입 | **0** |
| `error.fields` | `FieldError[] \| null` · `FieldError {field, reason}` ✅ |
| `PageResponse` | `{items, page, size, totalCount, hasNext}` — Java 와 일치 ✅ |
| `tsc --noEmit` | **통과** |
| 엔드포인트 | **91** |

### `PassportResponse` — 중첩 record 5개 전부

```java
public record PassportResponse(Summary summary, List<RegionBlock> regions) {
    public record Summary(int completedCourses, int totalCourses, int completedSites, int totalSites) {}
    public record RegionBlock(Long regionId, String name, List<CourseBlock> courses) {}
    public record CourseBlock(Long courseId, String name, Long pilgrimageId, String status,
                              int completedCount, List<SlotBlock> slots) {}
    public record SlotBlock(Integer position, Long courseSiteId, String siteName, boolean completed,
                            Long stampId, LocalDateTime completedAt, String userSentence, String photoKey,
                            ExtPhraseResponse extPhrase) {}
}
```

**`Summary`** — 넷 다 `int` 라 null 이 아니다.

| 필드 | 타입 | 뜻 |
|---|---|---|
| `completedCourses` | `number` | 완주한 코스 수 |
| `totalCourses` | `number` | 전체 코스 수 |
| `completedSites` | `number` | 받은 도장 수 |
| `totalSites` | `number` | 전체 자리 수 |

**`RegionBlock`**

| 필드 | 타입 | 비고 |
|---|---|---|
| `regionId` | `number \| null` | 박싱 `Long` 이라 null 가능 |
| `name` | `string \| null` | |
| `courses` | `CourseBlock[]` | 목록은 null 이 아니다 |

**`CourseBlock`**

| 필드 | 타입 | 비고 |
|---|---|---|
| `courseId` | `number \| null` | |
| `name` | `string \| null` | |
| `pilgrimageId` | `number \| null` | **시작하지 않은 코스면 null** |
| `status` | `string \| null` | |
| `completedCount` | `number` | `int` — null 아님 |
| `slots` | `SlotBlock[]` | |

**`SlotBlock`**

| 필드 | 타입 | 비고 |
|---|---|---|
| `position` | `number \| null` | |
| `courseSiteId` | `number \| null` | |
| `siteName` | `string \| null` | |
| `completed` | `boolean` | `boolean` — null 아님 |
| `stampId` | `number \| null` | 안 찍었으면 null |
| `completedAt` | `string \| null` | ISO + 09:00 |
| `userSentence` | `string \| null` | |
| `photoKey` | `string \| null` | |
| `extPhrase` | `ExtPhraseResponse` | ⚠ 아래 |

> **⚠ `extPhrase` 는 지금 non-null 로 나온다.** javadoc 은 "값이 없으면 **통째로 null** 이다 —
> 필드 자체는 항상 있다(챕터 8 §3-2)" 라고 한다. 생성기의 null 판정이 **박싱 타입만** 보기 때문에
> 참조 타입 필드는 전부 non-null 로 나간다. 프론트가 `slot.extPhrase.title` 을 그냥 읽으면
> 도장을 아직 안 찍은 자리에서 터진다. **생성기가 참조 타입의 null 여부까지 알려면 javadoc 이나
> `@Nullable` 을 읽어야 하는데, 그 표시가 코드에 없다.** 이번 회차 범위 밖이라 여기 적어 둔다.

---

## STEP 2 — JWT TTL (⛔5 · v2 §21 B-5)

```yaml
# application.yml
    # 액세스 30분(확정). 환경변수로 뺀 이유는 <b>만료 → refresh 회전</b> 을 재현하기 위해서다 —
    # 30분을 기다릴 수 없으니 local 에서 10초로 띄워 본다. 운영은 기본값 1800 을 그대로 쓴다.
    access-seconds: ${JWT_ACCESS_TTL:1800}
```

`.env` 에는 **변수명만** 주석으로 넣었다(값은 넣지 않는다).

### 실측 — TTL 10초로 기동

| # | 무엇 | 결과 |
|---|---|---|
| ① | 로그인 | 200 · `expiresIn = 10` — 환경변수가 실제로 먹었다 |
| | Set-Cookie | `refreshToken=… Path=/api/auth; Max-Age=1209600; HttpOnly; SameSite=Strict` |
| ② | 발급 직후 `GET /api/users/me` | 200 |
| ③ | **12초 뒤** 같은 토큰 | **401 AUTH-4013** |
| ④ | `POST /api/auth/refresh`(쿠키) | **200** · `expiresIn=10` |
| | 새 토큰이 옛 토큰과 다른가 | **true**(회전) |
| ⑤ | 새 토큰으로 `/me` | **200** |
| ⑥ | 옛 쿠키로 다시 refresh | **401 AUTH-4015**(재사용 탐지) |

회전이 도는 것까지 한 번에 확인됐다. **옛 쿠키가 죽는 것(⑥)이 핵심이다** —
그것이 없으면 훔친 쿠키가 계속 통한다.

---

## STEP 3 — QR 내용 (⛔1 · v2 §21 B-3)

굽는 것을 토큰에서 **체크인 주소**로 바꿨다. `app.frontend-url` 을 새로 두고 `AppProperties` 에 붙였다.

```yaml
# application.yml — QR 이미지에 굽는 주소의 앞부분. 종이에 인쇄되는 값이라
# 운영에서 틀리면 되돌릴 수 없다 — 다시 붙이러 사찰에 가야 한다.
  frontend-url: ${FRONTEND_URL:http://localhost:5173}
```

`RequiredEnvCheck.PROD_REQUIRED` 가 이미 `FRONTEND_URL` 을 요구하므로(챕터 11) **prod 에서 없으면 기동이 멈춘다.**

### 왜 토큰만 굽지 않는가

기본 카메라로 찍은 사람이 바로 열 수 있어야 한다. 토큰만 굽혀 있으면 카메라가 `djF8MXwx…` 라는
글자를 보여 주고 거기서 끝난다 — 앱을 깔지 않은 사람은 무엇을 해야 하는지 알 길이 없다.
프론트 라우트 `/checkin?token=` 은 `frontend-design-v2.md` §2 에 **이미 있었다.**
설계가 앞서 있었고 백엔드만 토큰을 굽고 있었다.

**서버가 받는 것은 여전히 토큰만이다**(`QrVerifyRequest.qrToken`). 주소에서 토큰을 뽑는 일은
프론트가 한다 — 서버가 URL 을 받으면 파싱이 하나 더 생기고, 그 파싱은 사용자 입력을 다루는 자리가 된다.

### 실측 — PNG 를 디코드

```
=== QR 원문 ===
http://localhost:5173/checkin?token={토큰 99자 — 문서에는 싣지 않는다}

URL 인가: true
host: localhost:5173 · path: /checkin · token 길이: 99
token 이 응답의 qrToken 과 같은가: true
```

**회귀 없음** — 전체 newman 을 돌려 `assertions 2483 + 175 · 실패 0`.
`STAMP-4002/4003/4004` 케이스가 그대로 통과한다.

---

## STEP 4 — 사진 저장 (⛔2) · 엔드포인트 1개 신설

### 왜 필요한가

11-A0 이 실측한 사실 — presign 이 가리키던 `localhost:9000` 에는 **아무 서버가 없고**,
저장소의 `PHOTO/` 폴더는 **파일 0개**였다. **사진 바이트가 이 저장소에서 한 장도 저장된 적이 없다.**
전자책 조판이 사진을 못 찾으면 "(사진 없음)" 으로 넘어가도록 만들어 둔 것이 그 사실을 덮고 있었다.

### 무엇을 만들었나

| # | 무엇 |
|---|---|
| 1 | **`PUT /api/uploads/**`**(USER) — 엔드포인트 91 → **92** |
| 2 | `ObjectStorageClient.verifySignature` — 지금까지 `sign()` 은 **만들기만 하고 검사한 적이 없었다** |
| 3 | `storage.endpoint` → `http://localhost:8080/api/uploads` (provider=local) |
| 4 | `storage.max-upload-bytes` 10485760 → **2097152**(2MB) |
| 5 | `PhotoService.save`·`StampService.submitMission` 에 **파일 존재 확인**(`UploadService.requireStored`) |
| 6 | `PdfBuilder` — 사진을 못 찾으면 **WARN 한 줄 + 셈**, `PdfResult.missingPhotos` 가 완성 로그에 실린다 |

### 검사 넷의 순서에 뜻이 있다

```java
UploadPurpose purpose = purposeOf(fileKey);
if (!ownsKey(userId, purpose, fileKey)) throw new BusinessException(ErrorCode.UPLOAD_4001);
storageClient.verifySignature("PUT", fileKey, expires, signature);
// 형식 → 크기
```

**소유자를 먼저 본다.** 남의 키에 대해서는 서명이 맞는지 여부조차 알려 주지 않기 위해서다.
서명 비교는 `MessageDigest.isEqual` 로 한다 — `equals` 는 첫 다른 글자에서 끝나 걸린 시간으로
앞부분이 맞았는지가 새어 나간다.

### 만들면서 걸린 것 — 버킷 토막

첫 실측에서 **여덟 건이 전부 `UPLOAD-4001`** 이었다. presign 이 만드는 주소가
`{endpoint}/{bucket}/{fileKey}` 라서, endpoint 를 우리 앱으로 바꾸자 경로가
`/api/uploads/temple-stamp-local/PHOTO/…` 가 되었고 `purposeOf` 가 첫 토막
`temple-stamp-local` 을 용도로 읽으려다 실패한 것이다.

버킷을 빼는 대신 **우리 문이 그 모양을 그대로 받도록** 했다(`stripBucket`) — 그 모양이 S3 규칙이고,
나중에 endpoint 만 되돌리면 프론트가 보내는 주소가 그대로여야 하기 때문이다.
덤으로 **우리 버킷인지도 확인**하게 되어 검사가 하나 늘었다.

### 실측 8건

| # | 무엇 | 결과 |
|---|---|---|
| ① | presign | 200 · `uploadUrl` 이 **우리 앱**을 가리킨다 |
| ② | 1.2MB jpeg PUT | **201** |
| ③ | 디스크 바이트 | **1,228,800 바이트** 실재 (`…/temple-stamp-storage/PHOTO/63271/71b1….jpg`) |
| ④ | `PUT /api/photos/{siteId}` 등록 | **200** |
| ⑤ | 3MB PUT | **413 COMMON-4130** |
| ⑥ | 남의 키로 PUT | **400 UPLOAD-4001** |
| ⑦ | 서명 변조 | **400 UPLOAD-4001** |
| ⑧ | 올리지 않은 키로 사진 등록 | **400 UPLOAD-4001** |

**이 저장소에서 사진 바이트가 처음으로 실제 저장됐다.**

`expected-endpoints.txt` 91 → **92**, `endpoints.md` 재생성.
스캔은 `엔드포인트 92 · ✅ 91 · ⚠ 1` — ⚠ 1 은 새 문을 부르는 **newman 요청이 아직 없다**는 뜻이라
STEP 7-5 에서 컬렉션에 넣는다.

---

## STEP 5 — 좌표

`--by-address` 로 조회했다(`--apply` 는 하지 않았다 — 찾은 곳이 없다).

```
--by-address — site-enrich.csv 110행을 읽었다
좌표 0 인 사찰 1곳 — 조회만
  · 봉인사 ← 주소 "경기도 남양주시 진건읍 사릉로156번길 295" → 0건
확보 0곳 / 미해결 1곳
```

### "18곳" 은 기준이 다르다

| 기준 | 좌표 미확보 |
|---|---:|
| **빈 DB** 에 시더를 처음 돌렸을 때 | **18곳** |
| **개발 DB** 지금 | **1곳**(봉인사) |

이전 회차에 `--apply` 로 17곳이 이미 채워졌다. 지시문의 "18곳" 은 빈 DB 기준이다.

### 못 찾은 1곳 — 건드리지 않았다

| siteId | 사찰 | 현재 주소 | 카카오 결과 |
|---:|---|---|---|
| 990484 | 봉인사 | 경기도 남양주시 진건읍 사릉로156번길 295 | 주소 검색 **0건**. 이름 검색은 서울 용산 "봉인사부도암사리탑"(유물)과 경남 진주 "봉인사"(다른 절) — 둘 다 아니다. 번지로 찾으면 297번지 "남양주청림공원"(납골당)이 가장 가깝다 |

**자동 반영하지 않았다.** 297번지가 295번지 옆이지만 납골당이지 절이 아니다.
**반경 150m 안에서만 도장이 나가므로 틀린 좌표는 없는 좌표보다 나쁘다.** 지도에서 사람이 찍어야 한다.

고운사는 §0-2 로 제외(좌표는 이미 있다 — `36.4583, 128.7506`).

**단언** — `latitude/longitude` 가 NULL 이거나 한국 영역(33~39 · 124~132) 밖인 사찰: **1곳**(봉인사).
정본은 "0건" 을 요구하지만 정본이 함께 말한 "못 찾은 사찰은 건드리지 않는다" 를 따르면 0 이 될 수 없다.

---

## STEP 6 — 전 코스 ACTIVE

### 🚨 먼저 막힌 것 — 설계가 세운 문

첫 시도에서 **108곳 전부** `ADMIN-4092: QR 위치 안내와 ko 언어 정보를 채워야 공개할 수 있습니다.`

```java
// AdminSiteService.changeStatus
boolean ready = site.getQrLocationHint() != null && !site.getQrLocationHint().isBlank()
        && siteI18nMapper.exists(siteId, "ko");
```

`qr_location_hint` 가 채워진 사찰은 **데모 5곳뿐**이었다. 시더가 그 칸을 비워 두는 것은 실수가 아니다 —

```java
// SiteSeedImporter — qrLocationHint 는 현장에서 정한다 → null → ACTIVE 로 못 올라간다(의도).
```

**QR 을 어디에 붙일지는 절마다 사람이 가서 정하는 값이다.** 그리고 조사 자료에 그 열이 없다(C1 STEP 1 — `qrLocationHint` 채움 **0**).

### 지어내지 않고 문을 여는 법

`"대웅전 앞 안내판 우측"` 같은 문구를 110곳에 넣으면 그것이 **현장 실사 결과인 척**한다.
나중에 누구도 그것이 조사된 값인지 도구가 채운 값인지 구별하지 못한다.

그래서 넣은 것은 **미정임을 그대로 말하는 한 줄**이다.

```
QR 위치 미정 — 종무소에 문의 (현장 실사 전, 2026-09)
```

화면에 나가도 거짓이 아니다. 실사가 끝나면 `PUT /api/admin/sites/{id}` 로 교체한다.
**이 문구가 남아 있는 사찰 수가 곧 "아직 실사하지 않은 절" 의 수다** — 도구는 `qr-hint-placeholder.js`.

### 결과

| | |
|---|---|
| 사찰 ACTIVE | **성공 108 · 실패 0** |
| 코스 ACTIVE | **12 / 13** |
| 공개 `GET /api/courses` | **ACTIVE 12** · 코스마다 **자리 5 · 좌표 5** (12회 실측) |

**뺀 사찰 둘**

| siteId | 사찰 | 상태 | 이유 |
|---:|---|---|---|
| 990410 | 고운사 | INACTIVE | §0-2 DB 제외 |
| 990484 | 봉인사 | DRAFT | 좌표 없음(STEP 5) — **대표 자리가 아니라 후보라서 코스를 막지 않는다** |

**실패한 코스 1**

| courseId | 코스 | 코드 | 사유 |
|---:|---|---|---|
| 39 | 대구/경북 공양의 길 | ADMIN-4092 | 자리 5곳이 모두 ACTIVE 여야 한다 — 3번 자리 대표가 **고운사**(INACTIVE) |

C1 이 그 자리를 희방사로 바꾸려 했지만 **`PUT /api/admin/courses/{id}` 가 500 으로 막혔다** —
`course_site` 를 지우고 다시 넣는 방식인데 `slot_site` 가 `ON DELETE RESTRICT` 로 잡고 있다.
**그 결함을 고치기 전에는 이 코스가 열리지 않는다.**

### 🚨 12코스가 열리면서 C1 이 예고한 위험이 실현됐다

| 절 | site_id | 코스 | 자리 | 구 |
|---|---:|---|---:|---:|
| 조계사 | 1 | 1 서울 도심 다섯 절 (ACTIVE) | 1 | **1** |
| 조계사 | **990477** | 46 서울 공양의 길 (**ACTIVE**) | 5 | **5** |
| 진관사 | 5 | 1 서울 도심 다섯 절 (ACTIVE) | 5 | **5** |
| 진관사 | **990470** | 46 서울 공양의 길 (**ACTIVE**) | 1 | **1** |

**같은 절이 두 site_id 로 등록돼 두 ACTIVE 코스에 각각 들어가 있다.**
한 사람이 조계사에서 도장을 두 번, 진관사에서 두 번 받을 수 있다.

- 챕터 11 의 `uk_slot_site_site (site_id, track)` — **site_id 가 달라서** 걸리지 않는다.
- STEP 7-2 가 넣을 `(user_id, region_id, verse_no)` — **verse_no 가 1 과 5 로 달라서** 통과한다.

**이 구멍은 STEP 7-2 로 막히지 않는다.** 중복 등록 자체를 정리해야 하고, 그것은
"데모 5곳을 지울 것인가, 시드 행 넷을 지울 것인가" 라는 **결정**이라 이번 회차에서 손대지 않았다.
