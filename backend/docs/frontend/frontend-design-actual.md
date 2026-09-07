# 프론트엔드 설계도 — 실제 코드 기준 (2026-09-07)

정본 `frontend-design.md` 의 화면 표를 **실제로 부를 수 있는 API 와 필드**로 다시 그린 것이다.
타입 초안은 `api-types.d.ts` — 컨트롤러와 record 선언에서 자동으로 뽑았다(엔드포인트 91 · DTO 104). 손으로 고치지 않는다.

표시: ✅ 정본대로 · 🔁 이름·모양이 다름(실제를 적었다) · ❌ 정본이 가정한 것이 없음 · ⬆ 서버가 더 줌

---

## 0. 모든 화면에 공통인 것

| 무엇 | 실제 |
|---|---|
| 성공 봉투 | `{ success: true, data: T, error: null, timestamp }` |
| 실패 봉투 | `{ success: false, data: null, error: { code, message, fields }, timestamp }` |
| 날짜 | 전부 ISO `+09:00` 문자열 |
| 목록 | 페이지 없는 목록은 `ItemsResponse<T>` = `{ items: T[] }` · 페이지는 `PageResponse<T>` = `{ items, page(0부터), size, totalCount, hasNext }` |
| "없음" | 404 가 아니라 **200 + `data: null`** (생각상자 회상 등) |
| **DELETE 다섯** | **204 · 본문 없음.** `res.json()` 을 부르면 파싱 오류가 난다 |
| 좌표 | **요청에 `latitude`·`longitude` 를 넣으면 400 `COMMON-4001`.** 타입에 그 필드가 아예 없다 |
| 상태코드 분포 | 200 × 80 · 201 × 6 · 204 × 5 |

---

## 1. 화면별 — 실제로 부를 것

| 경로 | 화면 | 부를 API | 판정 | 메모 |
|---|---|---|---|---|
| `/` | 인트로 3단 | 없음(프론트) · 가입 때 `tier` 를 넘긴다 | ✅ | |
| `/home` | 홈 | `GET /api/verses`(오관게) · `GET /api/passport`(다음 사찰) · `GET /api/thinkbox/flashback` · `GET /api/meditations` | 🔁 | **홈 전용 API 는 없다.** 넷을 병렬로 부른다 |
| `/regions` | 권역 9 | `GET /api/regions` → `ItemsResponse<RegionResponse>` | ✅ | 표는 10행(제주 포함) |
| `/regions/:regionId` | 권역 상세 | `GET /api/courses?regionId=` | ✅ | **권역 → 코스가 1:N** |
| `/courses/:courseId` | 코스 상세 | `GET /api/courses/{courseId}` → `CourseDetailResponse` | ⬆ | 아래 §2 |
| `/sites/:siteId` | 싱글페이지 9블록 | `GET /api/sites/{siteId}` → `SitePageResponse` | ✅ | 문구·과제가 여기 실린다 |
| `/stamp/:courseSiteId` | 3단계 인증 | `POST /api/stamps/{courseSiteId}/gps-check` → `POST /api/stamps/{stampId}/qr` → `POST /api/stamps/{stampId}/mission` | ✅ | 순서 고정 |
| `/checkin?token=` | QR 진입 | 2단계 `POST /api/stamps/{stampId}/qr` 로 이어진다 | ✅ | 진행 중 세션은 `GET /api/stamps/by-slot/{courseSiteId}` 로 찾는다 |
| `/passport` | 여권 | `GET /api/passport` → `PassportResponse` | ✅ | |
| `/photos/:siteId` | 사진·문장 | `POST /api/uploads/presign` → 저장소 직접 PUT → `POST /api/photos` | ✅ | **서버는 파일을 받지 않는다** |
| `/meditations` | 명상 | `GET /api/meditations` · `/{id}` · `POST /api/meditations/logs` | ✅ | 목록·상세는 비로그인 |
| `/thinkbox` | 생각상자 | `GET /api/thinkbox?sort=` · `POST` · `PATCH` · **`DELETE` → 204** | 🔁 | 삭제 응답이 바뀌었다 |
| `/ebooks` | 전자책 | `POST /api/ebooks`(202) · `GET /api/ebooks` · `GET /api/ebooks/{id}` · `POST /api/print-orders` · `DELETE /api/print-orders/{id}`(204) | 🔁 | 아래 §3 |
| `/certificates` | 인증서 | `GET /api/certificates` · `GET /api/certificates/{id}` | ✅ | |
| `/verify/:serial` | 진위 확인 | `GET /api/certificates/verify/{serialNo}` → `CertificateVerifyResponse` | ✅ | 7필드 · 비로그인 |
| `/rewards` | 보상 | `GET /api/rewards` · `POST /api/rewards/{id}/claim` | ✅ | `claimable` 은 서버가 계산 |
| `/settings` | 설정 | `GET·PATCH /api/users/me` · `GET·POST /api/users/me/agreements` · `DELETE …/{type}`(204) · `DELETE /api/users/me`(204) | 🔁 | 철회가 목록을 돌려주지 않는다 |
| `/login` `/signup` | | `POST /api/auth/signup`(201) · `login` · `refresh` · `logout` | ✅ | |
| `/guide` | 가는 법 | `GET /api/content/**` | ✅ | |
| `/editor/*` | 원고 | `/api/editor/manuscripts/**` 4문 | ⬆ | 정본에 "신설" 로만 있다 |
| `/admin/*` | 관리자 | 34문 | ⬆ | |

---

## 2. 코스 상세 — 정본이 물은 필드

`GET /api/courses/{courseId}` → `CourseDetailResponse`

| 정본이 가정 | 실제 | 판정 |
|---|---|---|
| 자리 5 | `sites: CourseSiteResponse[]` | ✅ |
| 후보 사찰 | `sites[].candidates: CandidateResponse[]` | ✅ |
| `congested` | `candidates[].congested: boolean` | ✅ |
| 좌표 일괄 | `sites[].latitude`·`longitude`·`verifyRadius` | ✅ |
| QR 위치 힌트 | `sites[].qrLocationHint` | ✅ |
| **원고 미리보기** | **없다** — 문구·과제는 싱글페이지(`GET /api/sites/{siteId}`)의 `phrase`·`mission` 블록에 있다 | ❌ |
| — | `progress: ProgressResponse` (진행률이 함께 온다) | ⬆ |

후보에는 `track`·`sortNo`·`routeNote`·`isStar`·`servingNote` 도 실린다(⬆).
**혼잡(`congested: true`)은 오류가 아니라 배지다** — 목록에서 빼지 않고 표시만 한다.

---

## 3. 미션 응답 · 전자책 · 인증서 · 보상

### 미션 제출 `POST /api/stamps/{stampId}/mission` → `MissionResultResponse`

| 정본이 가정 | 실제 | 판정 |
|---|---|---|
| 원고 | `missionManuscript`(`GET /api/stamps/{id}` 쪽) · 제출 응답에는 `extPhrase` | 🔁 |
| 확장문구 | `extPhrase: ExtPhraseResponse` | ✅ |
| `courseCompleted` | `courseCompleted: boolean` | ✅ |
| `certificateSerial` | `certificateSerial: string \| null` | ✅ |
| `rewards` | `rewards: RewardResponse[]` | ✅ |
| — | `progress`·`status`·`message` | ⬆ |

이동시간 미달이면 `status: "PENDING"` 이고 `rewards` 는 비어 있다 — 실패가 아니라 **보류**다.

### 전자책 `EbookResponse`

| 정본이 가정 | 실제 | 판정 |
|---|---|---|
| `status` | `status`(REQUESTED/READY/FAILED) | ✅ |
| `downloadUrl` | `downloadUrl`(READY 일 때만 · **10분 서명**) | ✅ |
| **`cancelable`** | **없다.** 취소 가능 여부는 `PrintOrderResponse.status === 'REQUESTED'` 로 판단한다 | ❌ |
| — | `downloadable`·`pageCount`·`byteSize`·`failReason`·`ebookType`·`courseName` | ⬆ |

요청은 **202 REQUESTED** 로 답하고 곧바로 파일이 생기지 않는다. 목록을 폴링하거나 다시 열 때 `READY` 를 본다.

### 인증서 `CertificateResponse`

`certificateId`·`serialNo`·`certType`·`status`·`courseName`·`issuedAt`·`revokedAt`·`verifyUrl`·`downloadUrl` — ✅ · ⬆(`verifyUrl` 추가).
**회수되면 `status: "REVOKED"` 이고 `downloadUrl` 이 `null` 이다.** 목록에서 사라지지 않는다.

### 진위 확인 `CertificateVerifyResponse` — 7필드 ✅

`serialNo`·`certType`·`status`·`courseName`·`holderMasked`·`issuedAt`·`revokedAt`.
`holderMasked` 의 별표는 **이름 길이와 무관하게 항상 한 개**다. 없는 번호만 404 이고, 회수본은 200 + `REVOKED` 다.

### 보상 `RewardResponse`

`claimable: boolean` ✅ — 서버가 매번 계산한다. 프론트가 상태로 유추하지 않는다.

---

## 4. 캐시 · 무효화 (실제 API 이름으로)

| 데이터 | 쿼리 키 | staleTime | 무효화 시점 |
|---|---|---|---|
| 여권 | `['passport']` | 60초 | 도장 발행 성공 |
| 홈 | `['verses']` `['flashback']` `['meditations']` | 60초 / 24시간 | 도장 발행 성공(여권 쪽만) |
| 권역·코스 목록 | `['regions']` `['courses', regionId]` | 60초 | 도장 발행 성공 |
| 코스 상세 | `['course', courseId]` | 60초 | 도장 발행 성공(`progress` 가 바뀐다) |
| 싱글페이지 | `['site', siteId]` | **0 — 캐시하지 않는다** | 매번 새로(문구가 매번 달라진다) |
| 진행 중 도장 | `['stamp', stampId]` `['stampBySlot', courseSiteId]` | 0 | 단계마다 |
| 생각상자 | `['thinkbox', sort]` | 60초 | 쓰기·수정·삭제 성공 |
| 전자책 | `['ebooks']` `['ebook', id]` | 60초 | 요청·청소기 완료 뒤 재조회 |
| 인쇄주문 | `['printOrders']` | 60초 | 신청·취소 성공 |
| 인증서 | `['certificates']` | 60초 | 완주 성공 |
| 보상 | `['rewards']` | 60초 | 수령 신청 성공 |
| 명상 | `['meditations']` | 24시간 | 언어 변경 |
| 언어 변경 | — | — | 전체 무효화 |

**도장 발행(`POST …/mission` 200 + `status: "COMPLETED"`) 성공 시 무효화할 키**
`['passport']` · `['course', courseId]` · `['courses', regionId]` · `['regions']` · `['rewards']` · `['certificates']`(응답에 `certificateSerial` 이 있으면) · `['ebooks']`(`courseCompleted: true` 면 코스본이 큐에 들어간다).

---

## 5. 타겟 3종 · 다국어

| 무엇 | 실제 |
|---|---|
| `tier` 가 오가는 곳 | 가입 요청 → `users.tier` 저장 · `GET /api/users/me` 응답 `tier` · `PATCH /api/users/me` 로 변경 |
| tier 로 갈리는 것 | **싱글페이지의 문구·과제만** — `expansion_phrase`·`mission` 이 tier 축을 갖는다. 없으면 `AGE30` 으로 한 번 물러난다 |
| 도장에 남는 글 | `manuscript`(사찰 × 구절) — **tier 를 모른다.** 싱글페이지에서 본 글과 다를 수 있다 |
| 콘텐츠 i18n | `GET /api/content/**`(사전) · `GET /api/sites/{id}`(사찰 i18n) · `GET /api/meditations`(명상 i18n) — `locale` 로 갈린다 |
| **원고·확장문구 다국어** | **없다.** FOREIGN 타겟은 사찰 설명·명상까지만 번역된다(감사 H §7 ⛔) |

---

## 6. 오류 → 화면

| 코드 | 언제 | 화면이 할 일 |
|---|---|---|
| `AUTH-4013` | 토큰 없음 | 로그인으로 |
| `AUTH-4012`·`4014`·`4015` | refresh 무효·재사용 | **전 기기 로그아웃** 안내 |
| `USER-4031` | 위치 동의 전 GPS 시도 | 동의 화면으로 보내고 다시 시도 |
| `STAMP-4000`·`4001` | 반경 밖 · 정확도 낮음 | 다시 시도 또는 **예외 접수**(하루 2건) |
| `STAMP-4004` | 다른 사찰 QR | "이 사찰의 QR 이 아닙니다" |
| `STAMP-4091` | 60분 지남 | 처음(도착 확인)부터 다시 |
| `STAMP-4090` | 이미 완료 | 여권으로 |
| `STAMP-4291`·`4292` | 하루 5 · 예외 2 초과 | 내일 다시 |
| `COMMON-4001` | 요청에 좌표가 들어감 | **프론트 버그다.** 개발 모드에서 즉시 throw |
| `COMMON-4000` | 검증 실패 | `error.fields` 를 입력칸에 붙인다 |
| `EBOOK-4001` | 기록이 없음 | "도장을 먼저 찍어 주세요" |
| `EBOOK-4290` | 하루 3권 초과 | 내일 다시 |
| `PRINT-4001` | 아직 안 만들어진 책 | READY 를 기다린다 |
| `PRINT-4090`·`4091` | 취소 불가 상태 · 중복 신청 | 목록을 다시 불러 상태를 보여 준다 |
| `SITE-5001` | 문구가 준비되지 않음 | 콘텐츠 공백이다 — 운영에 알린다 |
