# temple-stamp — 프론트 전달 목록 (2026-09-07)

정리.md §4 를 그대로 뽑은 것이다. 원본이 바뀌면 이 파일도 다시 뽑는다 — 두 곳을 손으로 맞추지 않는다.
API 는 91 개, 응답 봉투는 `success·data·error·timestamp` 넷으로 고정이고 시각은 전부 `+09:00` 이다.
서버는 좌표를 받지 않는다(§4-10). 없음은 404 가 아니라 200 + null 로 온다(§4-7).
**DELETE 는 전부 204 · 본문 없음이다(§4-16).** **콘텐츠 이름은 넷이고 출처가 둘이다(§4-17).**

---


프론트가 모르면 **조용히 깨지는 것**만 모았다.

## 4-1. 경로·코드가 바뀐 것

| 프론트가 아는 것 | 실제 |
|---|---|
| `POST /api/auth/reissue` | **`POST /api/auth/refresh`** |
| 중복 이메일 `AUTH-4091` | **`AUTH-4090`** |
| 계정 잠금 `401` | **`403 AUTH-4031`** |
| `GET /api/pilgrimages/passport` | **`GET /api/passport`** |

**계정 잠금이 403 인 것이 특히 중요하다.** 흔한 재시도 구현이 "401 이면 refresh 한 번 치고 재시도"
인데, 잠금은 403 이라 그 분기에 안 걸리고 그냥 실패한다. 남은 시간은 응답 `message` 에 들어 있다.
**5회째 실패에서 바로 403** 이 나간다(6회째가 아니다).

## 4-2. 재발급 실패가 3종

| 코드 | 뜻 | 프론트가 할 일 |
|---|---|---|
| `AUTH-4014` | 쿠키·토큰 없음 | 조용히 로그인 화면 |
| `AUTH-4012` | 만료 | 조용히 로그인 화면 |
| **`AUTH-4015`** | **재사용 탐지 — 전 기기가 끊긴 상태** | **이유를 알려 주는 문구가 필요** |

`AUTH-4015` 에서 말없이 로그인 화면만 띄우면, 실제 탈취 상황에서 사용자가 아무것도 눈치채지 못한다.
예: "보안을 위해 모든 기기에서 로그아웃했습니다. 다시 로그인해 주세요."

## 4-3. 약관 — POST 는 배열, DELETE 는 경로변수

```json
POST /api/users/me/agreements
[{"agreementType":"LOCATION_SERVICE","version":1}]     ← 한 건이어도 배열
```

```
DELETE /api/users/me/agreements/{agreementType}        ← 본문 없음. 버전도 받지 않는다
→ 204 No Content (응답 본문 없음)
```

옛 `DELETE /api/users/me/agreements`(본문에 객체)는 없어졌다.

**2026-09-07 변경 — 철회 응답이 `200 + 남은 목록` 에서 `204 + 본문 없음` 으로 바뀌었다.**
남은 목록이 필요하면 `GET /api/users/me/agreements` 를 한 번 더 부른다. 아래 §4-16 을 함께 볼 것.

## 4-17. 콘텐츠 이름 넷 — 서로 다른 출처다 (챕터 10)

한 화면에 두 체계의 글이 함께 나온다. **같은 글이 나올 것이라고 가정하면 안 된다.**

| 부르는 이름 | 어디서 오나 | 언제 정해지나 | 응답 필드 |
|---|---|---|---|
| **확장문구** | `expansion_phrase` (tier × 구절 × 버전) | 싱글페이지를 열 때마다 | `GET /api/sites/{id}` → `phrase.{expansionPhraseId, versionNo, text}` |
| **행동과제** | `mission` (tier × 구절) | 싱글페이지 미리보기 · 실제 배정은 QR 통과 시 | `GET /api/sites/{id}` → `mission.{missionId, variantNo, body}` |
| **미션 안내** | `manuscript` (사찰 × 구절, kind=MISSION) | **QR 통과 시 도장에 박힌다** | `GET /api/stamps/{id}` → `missionManuscript` |
| **도장 문구** | `manuscript` (사찰 × 구절, kind=EXT) | **다짐 제출 시 도장에 박힌다** | `POST …/mission` · `GET /api/stamps/{id}` → `extPhrase` |

앞 둘은 **tier 로 갈리고**(없으면 AGE30 으로 물러난다), 뒤 둘은 **사찰로 갈린다**(tier 를 모른다).
도장에 박힌 뒤에는 원고가 퇴역·수정돼도 그 도장의 글은 바뀌지 않는다.

## 4-16. DELETE 는 전부 204 · 본문 없음 (2026-09-07 변경)

삭제 다섯 중 둘만 204 였고 셋은 `200 + 봉투` 였다. 같은 행위가 자리마다 다른 답을 내면
프론트가 자리마다 다르게 분기해야 한다 — 감사 G 가 계약 어긋남으로 잡아 **다섯을 204 로 통일**했다.

| 엔드포인트 | 전 | 후 |
|---|---|---|
| `DELETE /api/users/me` (탈퇴) | 204 | 204 (그대로) |
| `DELETE /api/print-orders/{id}` (인쇄 취소) | 204 | 204 (그대로) |
| `DELETE /api/thinkbox/{id}` | 200 + `{success,data:null,…}` | **204 · 본문 없음** |
| `DELETE /api/users/me/agreements/{type}` | 200 + **남은 목록** | **204 · 본문 없음** |
| `DELETE /api/admin/sites/{siteId}/elements/{code}` | 200 + 봉투 | **204 · 본문 없음** |

**프론트가 고칠 것 둘.** ① 세 곳의 성공 판정을 `res.ok` 나 `status === 204` 로 바꾼다 —
204 에는 본문이 없어 `res.json()` 을 부르면 파싱 오류가 난다.
② 약관 철회 뒤 남은 목록이 필요하면 `GET /api/users/me/agreements` 를 한 번 더 부른다.

실패는 그대로다 — 없는 자원은 404, 남의 것은 404, 상태가 안 맞으면 409 이고 그때는 봉투가 실린다.

## 4-4. 관리자 등록·수정 본문에 `status` 금지 — 400

`POST`·`PUT /api/admin/sites` 본문에 `status` 를 실으면 **400 COMMON-4004** 다(모르는 필드를 버리지 않는다).
공개 여부를 바꾸는 길은 **`PATCH /api/admin/sites/{id}/status` 하나뿐**이다 —
그쪽에서 ACTIVE 전환 조건(QR 위치 안내·ko 언어 정보)을 검사하기 때문이다.
등록은 **언제나 DRAFT** 이고, 내용만 고치는 `PUT` 은 공개 여부를 건드리지 않는다.

## 4-5. 내려간 코스·사찰은 직접 링크로도 열리지 않는다

| 경로 | ACTIVE 아닐 때 |
|---|---|
| `GET /api/courses/{id}` | **404 COURSE-4041** |
| `GET /api/sites/{id}` · `/page` · `/guide` | **404 SITE-4040** |

관리자 경로(`/api/admin/**`)는 그대로 status 를 가리지 않는다.

## 4-6. 그 밖에

- **`/api/regions/` 에 슬래시를 붙이면 401.** Boot 3 는 trailing slash 매칭이 꺼져 있어
  매처에 안 걸리고 `anyRequest().authenticated()` 로 떨어진다. `/api/courses/**`·`/api/sites/**` 는 무관.
- **`GET /api/sites/{id}/guide` 는 Accept-Language 를 받지 않는다.** 형제 API 는 전부 받는데 이것만 다르다.
  참배 순서 사전이 한국어 한 벌뿐이라, 지금 언어를 받으면 절 이름만 번역되고 본문은 한국어인 반쪽 응답이 된다.
- **`X-Request-Id` 는 32자.** 문의 시 이 값을 함께 받으면 서버 로그에서 그 요청만 바로 찾는다.
  클라이언트가 보내면 서버가 그 값을 그대로 쓴다(64자 이하일 때).
- **`position` 이 두 곳에 있고 뜻이 다르다.** 「가는 법」은 참배 순서 **1~7**, 코스는 자리 **1~5**.
  응답 객체가 달라 섞이지는 않지만 타입 정의를 공유하지 말 것.
- **「가는 법」 `steps` 는 언제나 7칸.** 없는 요소도 자리를 지키며 `present:false` 이고
  `localName`·`etiquette`·`note` 가 `null` 이다. 없는 것을 빼서 렌더링하면 절마다 화면 구성이 달라진다.
- **`GET /api/sites`(공개 검색)·`GET /api/courses/{id}/progress`·`GET /api/pilgrimages`(목록)는 없어졌다.**
  앞의 둘은 404, 마지막은 405(같은 경로에 POST 가 남아 있어서).
- **courseCount 가 0 인 권역은 "준비 중" 으로 표시하고 지도에서 점등하지 않는다.**
  제주가 그렇다 — v4 후보 편성에 아직 없어 코스가 0 이다. 목록에는 정상적으로 나간다.
- **`candidates[].congested = true` 는 과포화 사찰 — 목록에 남기되 "혼잡" 배지, 지도 강조 안 함.**
- **`GET /api/content/i18n/{lang}` 은 없는 언어에 404 가 아니라 ko 를 돌려준다**(명세 F-20 "미번역 시 ko 폴백").
  응답 본문의 **`lang` 필드가 실제로 나간 언어**다 — 요청한 `{lang}` 이 아니라 그 값을 믿을 것.
  지원 언어는 `ko·en·ja·zh` 넷이고, 그 밖을 요청하면 `lang: "ko"` 가 온다.
- **확장문구 반복 방지가 챕터 5 부터 동작한다.** 노출 이력(`phrase_seen`)은 **도장이 COMPLETED 로 넘어갈 때만**
  남는다 — 싱글페이지에서 미리 본 것은 기록되지 않는다. 다짐 제출 본문에 `expansionPhraseId` 를 실어 보내야
  기록되고, 서버가 그 구절·그 계층의 문구인지 확인한 뒤에만 쓴다(틀려도 제출은 성공한다).

## 4-7. 응답 규약 (변경 없음, 확인용)

```json
{ "success": true,  "data": {...}, "error": null, "timestamp": "2026-09-06T...+09:00" }
{ "success": false, "data": null,  "error": { "code": "AUTH-4011", "message": "...", "fields": null }, "timestamp": "..." }
```

- **`null` 필드도 키가 나간다.** 키 없음과 값 `null` 을 구분하지 말 것.
- 검증 실패(`COMMON-4000`)일 때만 `error.fields` 에 필드별 사유가 배열로 들어간다.
- `timestamp` 는 `+09:00` 오프셋을 포함한다.

## 4-8. 코스 자리(position) 중복은 400 검증 오류 (2026-09-06 변경)

`PUT/POST /api/admin/courses` 본문의 `sites[].position` 이 겹치면 이전에는 **404 `COURSE-4042`** 였다.
"코스 자리를 찾을 수 없습니다" 라는 뜻의 코드라 화면에 그대로 띄우면 관리자가 무엇을 고쳐야 할지 알 수 없었다.

```
400 { "code": "COMMON-4000", "fields": [ { "field": "sites", "message": "자리 번호가 겹칩니다." } ] }
```

**`COURSE-4042` 는 이제 "그 자리가 실제로 없다"(조회 실패) 에만 쓴다.**

## 4-9. 코스 상세의 자리마다 `candidates` 가 붙는다 (v4 신규)

`GET /api/courses/{id}` 의 `sites[]` 각 원소에 필드 하나가 늘었다.

```
sites[].candidates: [{ siteId, siteName, track, sortNo, routeNote, isStar, servingNote }]
```

- **한 자리(구)에서 인증할 수 있는 사찰이 여럿이다.** 대표(`sites[].siteId`)도 이 목록에 들어간다(track MAIN, sortNo 1).
- `track` 은 `MAIN`(공양의 길) / `SUNROAD`(선로드). **`?target=RIDER` 면 SUNROAD 가 앞으로 온다** — 내용은 같고 순서만 다르다.
- **`congested = true` 는 과포화 사찰이다.** 목록에서 빼지 않는다 — 그 자리의 유일한 후보가 과포화인 경우가 있어
  (부산·동부 1·2구) 빼면 목록이 통째로 비어 "갈 곳이 없다" 로 읽힌다. **"혼잡" 배지를 붙이고 지도에서 강조하지 않는다.**
- **정렬은 track 이 1차, congested 가 2차다.** `?target=RIDER` 는 SUNROAD 를 앞으로 올리고,
  같은 track 안에서는 혼잡하지 않은 곳이 먼저다. 혼잡 여부가 track 순서를 뒤집지는 않는다.
- 시드가 넣은 사찰은 `localName` 이 null 인 요소가 있다. **null 이면 사전 이름(일주문·천왕문 등)으로 표시**한다.

## 4-10. 스탬프 3단계 — 경로와 본문이 바뀌었다 (챕터 5)

| 바뀐 것 | 내용 |
|---|---|
| **경로** | `POST /api/stamps/{stampId}/qr-verify` → **`/qr`** (옛 경로는 404) |
| **1단계 본문** | `{siteId, withinRadius, accuracyGrade}` — **`siteId` 필수(v4)**. 그 자리의 후보가 아니면 400 `COURSE-4001` |
| **증빙 본문** | `{courseSiteId, siteId, sentence, photoKey}` — **`siteId` 필수(v4)**. GPS 경로와 같은 후보 검증을 받는다 |
| **3단계 본문** | `expansionPhraseId` 선택 추가. 서버가 이 구절·이 계층의 것인지 확인한 뒤에만 기록한다(틀려도 제출은 성공) |
| **새 조회** | `GET /api/stamps/by-slot/{courseSiteId}` — 그 자리에서 받은 완료 도장. 없으면 404 |
| **응답 4필드** | `StampStatusResponse` 맨 뒤에 `siteId`·`siteName`·`photoKey`·`userSentence` |

```
409 STAMP-4090  "이미 발행된 스탬프입니다 — 조계사 · 2026-09-06"
```

**이 409 를 받으면 곧바로 `GET /api/stamps/by-slot/{courseSiteId}` 로 그 도장을 보여준다.**
사찰·날짜·사진·다짐이 그 응답에 다 있다.

새 에러코드 3종 — `STAMP-4031`(403 남의 스탬프) · `STAMP-4291`(429 하루 5개) · `STAMP-4292`(429 예외접수 2건).
**`STAMP-4001`(정확도 LOW)과 `STAMP-4000`(반경 밖)을 헷갈리지 말 것** — 앞은 "다시 측위", 뒤는 "더 가까이" 다.

### 정확도 등급 — 경계는 프론트가 갖는다 (챕터 11 결정 F)

서버는 좌표를 받지 않는다. 그래서 `accuracyGrade` 는 **단말이 판정해서 보내는 값**이고,
서버는 그 이름만 받는다. `location.js` 가 `navigator.geolocation` 의 `coords.accuracy`(미터)를 이렇게 접는다.

| `coords.accuracy` | 보낼 `accuracyGrade` | 화면 |
|---|---|---|
| ≤ 30m | `HIGH` | 그대로 1단계 호출 |
| 30m 초과 ~ 100m 이하 | `MID` | 그대로 1단계 호출 |
| 100m 초과 · 값 없음 | `LOW` | **서버를 부르지 않는다.** E-02 재측위 안내 + 30초 재시도 |

- 같은 숫자가 서버의 `AccuracyGrade` javadoc 에도 적혀 있다. **바꿀 때는 두 곳을 함께** 바꾼다 —
  한쪽만 고치면 같은 상황이 기기마다 다른 등급으로 올라온다.
- 현장 실측 전 값이다(인계문 기준). 실측 뒤 조정한다.
- `LOW` 를 그래도 보내면 서버가 `STAMP-4001` 로 막는다. 화면이 먼저 막는 것은 헛걸음을 줄이려는 것이지
  검사를 대신하는 것이 아니다.

### 이동시간 검사가 켜졌다 (챕터 11 결정 D·E)

직전 도장에서 너무 빨리 다음 도장을 찍으면 3단계 응답이 `status: "PENDING"` 으로 온다
(에러가 아니다 — 200 이다). 문구는 서버가 준다: "직전 사찰에서 이동하기에 이른 시간이라 확인이 필요합니다."

- **범위가 사용자 단위다.** 코스를 갈아타도 "직전에 완료한 도장" 과 비교한다 —
  A코스를 끝내고 5분 뒤 B코스 첫 자리를 찍어도 걸린다.
- 등록되지 않은 사찰 쌍은 **기본 15분**으로 판정한다. 즉 **모든 쌍에서 검사가 돈다.**
- PENDING 은 관리자 확인을 기다리는 상태다. 화면은 "발행됨" 이 아니라 "확인 중" 으로 그린다 —
  이때 응답의 확장문구는 아직 없다(문구는 승인 시점에 박힌다).

### 보상 목록에 `legacy` 가 붙었다 (챕터 11 결정 A)

살아 있는 사은품은 둘뿐이다 — **전자일기장**(3코스마다, `DIGITAL`, 신청 없음)과
**맞춤형 특별앨범**(12코스 완주, `PHYSICAL`, 수령 신청·심사).
도장 보상·코스 쿠폰은 껐지만 **이미 받은 행은 남는다** — 그 행에 `legacy: true` 가 붙는다.
`legacy` 가 참이면 `claimable` 은 언제나 false 이고, 눌러도 서버가 `REWARD-4001` 로 막는다.
화면은 "지난 혜택" 으로 갈라 그린다.

**하루 5개는 "오늘 만든 도장 중 살아 있는 것"** 으로 센다 — 진행 중·보류(PENDING)도 들어간다.
만료·반려만 빠진다. 그래서 보류가 4건이면 그날 새로 시작할 수 있는 것은 1건뿐이다.
서버 시간대(Asia/Seoul) 자정에 초기화된다.

**현장 QR 은 만료되지 않는다** — 회전(관리자 `POST /api/admin/sites/{siteId}/qr/rotate`)으로만 무효화된다.
"QR 유효시간 만료" 화면은 만들지 않아도 된다.

## 4-11. 기록 — 사진·문장·생각상자 (챕터 6)

| 바뀐 것 | 내용 |
|---|---|
| **`isPublic` → `isPrivate`** | 생각상자 요청·응답 전부. **`true` 가 비공개다** — 값을 그대로 옮기면 정반대가 된다 |
| **presign** | `GET /api/uploads/presign?purpose=PHOTO&contentType=image/jpeg` — `contentType` **필수**. `image/jpeg`·`image/png` 만. 확장자가 그것을 따라간다 |
| **새 API** | `PUT /api/photos/{siteId}` · `GET /api/photos/{siteId}` · `GET /api/photos` |
| **새 API** | `GET /api/thinkbox/flashback` — 여섯 달 전 오늘. **없으면 200 + `data: null`**(404 아님) |
| **목록 정렬** | `GET /api/thinkbox?sort=date\|course\|site` — 그 밖의 값은 400 |
| **명상 상세** | 응답에 `lang` 추가 — **실제로 나간 언어**다(요청한 locale 이 없으면 ko) |

```
PUT /api/photos/{siteId}
{ "photoKey": "PHOTO/{내번호}/{uuid}.jpg", "sentence": "왜 찍었는지 한 줄", "hasOtherFace": false, "isPrivate": false }
```

- **서버는 사진 바이트를 받지 않는다.** presign 으로 받은 `uploadUrl` 에 앱이 직접 PUT 하고,
  그때 받은 `fileKey` 만 위 본문에 담는다. **EXIF 제거는 앱 몫**이다 — 서버는 확인할 방법이 없다.
- **사찰당 사진 1장·문장 1개.** 다시 보내면 새로 생기지 않고 교체된다(항상 200).
- 남의 키를 넣으면 400 `COMMON-4000` + `fields[photoKey]`, 공개되지 않은 사찰이면 404 `SITE-4040`.
- 도장 사진(`stamp.photo_key`)과 이 사진은 **별개**다. 전자책은 둘 다 싣고 `isPrivate`·`hasOtherFace` 는 뺀다.
- **같은 사찰의 DIRECT 문장은 자리가 하나다** — `PUT /api/photos/{siteId}` 와 `POST /api/thinkbox {siteId}` 가
  같은 자리를 쓴다. 나중에 쓴 것이 앞 것을 덮고, 본문이 실제로 바뀌면 `isEdited` 가 켜져 흔적이 남는다.
  **사진 없이 문장만** 남기려면 `POST /api/thinkbox` 에 `siteId` 를 달아 쓰면 된다.
- **관리자 사찰 목록에 `status` 가 온다** — `GET /api/admin/sites` 응답의 각 항목에 `DRAFT`/`ACTIVE`/`INACTIVE`.
  `?status=DRAFT` 로 걸러 볼 수도 있다(안 주면 전부). 공개 응답(`GET /api/sites/{id}`)에는 이 필드가 없다 —
  공개된 것만 나가므로 필요가 없고, 넣으면 사용자 화면에도 따라 나간다.

## 4-12. 완주·인증서·보상 (챕터 7 · 보강 반영)

### 보상 상태 기계

```
GRANTED ──신청──▶ CLAIMED ──관리자 승인──▶ PAID
   │                 │
   │                 └──관리자 반려(사유 필수)──▶ REJECTED ──다시 신청──▶ CLAIMED
   │
   └──완주 취소──▶ REVOKED        감사 점수가 높으면 신청이 UNDER_REVIEW 로 간다
```

| 상태 | 사용자에게 보이는 뜻 |
|---|---|
| `GRANTED` | 받았다. 실물이면 지금 신청할 수 있다 |
| `CLAIMED` | 신청 접수됨. 심사를 기다린다 |
| `UNDER_REVIEW` | 확인 중(감사 점수가 높아 사람이 본다). 사용자에게는 `CLAIMED` 와 같은 문구로 충분하다 |
| `PAID` | 지급 확정. 송장이 있으면 관리자 쪽에 남아 있다 |
| `REJECTED` | 이번 신청이 반려됐다. **다시 신청할 수 있다** |
| `REVOKED` | 근거가 된 완주가 취소돼 회수됐다 |

**허용되는 전이는 이 다섯뿐**이고, 그 밖은 전부 409 `REWARD-4090` 이다.

| 전이 | 누가 |
|---|---|
| `GRANTED → CLAIMED`(또는 UNDER_REVIEW) | 사용자 신청 |
| `REJECTED → CLAIMED` | 사용자 재신청 |
| `CLAIMED·UNDER_REVIEW → PAID` | 관리자 승인 |
| `CLAIMED·UNDER_REVIEW → REJECTED` | 관리자 반려(사유 1~200자 필수) |
| `GRANTED → REVOKED` | 완주 취소(자동) |

- **발송은 별도 상태가 아니다.** 승인(`PAID`) 전이 때 송장 문자열(선택, 0~100자)을 함께 받는다.
- **이미 신청·지급된 실물은 완주가 취소돼도 `REVOKED` 가 되지 않는다.** 상태는 그대로 두고 관리자 쪽에만
  "사람이 볼 것" 표시가 켜진다 — 택배가 이미 나갔을 수 있어서다. 사용자 화면에서는 아무 일도 일어나지 않는다.

### claimable — 서버가 매번 계산한다

```
claimable = rewardType == "PHYSICAL"
        AND status in ("GRANTED", "REJECTED")
        AND 내 보상
```

- 눌러도 되는지는 **서버가 다시 본다.** 목록의 `claimable` 을 안 보고 눌러도 400/409 로 막힌다.
- 실물이 아닌 보상(`STAMP`·`COUPON`)에 신청하면 **400 `REWARD-4001`** — 버튼 자체를 만들지 말 것.
- 이미 신청·처리된 것에 다시 누르면 **409 `REWARD-4092`**.
- 남의 보상에 신청하면 403 `AUTH-4032`(다른 사람의 자원과 같은 코드).

### 수령 신청 본문 — 네 필드 (2026-09-06 보강에서 추가)

```json
POST /api/rewards/{userRewardId}/claim
{ "recipientName": "홍길동", "phone": "010-1234-5678",
  "address": "서울시 종로구 …", "memo": "부재 시 경비실" }
```

| 필드 | 규칙 |
|---|---|
| `recipientName` | 필수 1~50자 |
| `phone` | 필수, `0-9 + ( ) . -`와 공백만 7~30자 |
| `address` | 필수 1~200자 |
| `memo` | 선택 0~200자 |

- **빈 본문으로 부르면 400 `COMMON-4000`** 이고 `error.fields` 에 어느 필드인지 들어온다.
- **이 값들은 보상 목록 응답에 실리지 않는다.** `GET /api/rewards` 에 `address`·`recipientName`·
  `phone`·`memo` 키는 없다 — 배송 정보는 별도 표에 있고 관리자 심사 화면에서만 보인다.
- 반려 뒤 다시 신청하면 배송 정보는 **덮어쓴다**. 주소가 바뀌어 다시 신청하는 경우가 있다.

### 인증서 — 회수돼도 목록에서 사라지지 않는다

| 필드 | 뜻 |
|---|---|
| `status` | `VALID` / `REVOKED` — 회수본도 목록에 남는다. 사라지면 사용자는 이유를 알 수 없다 |
| `revokedAt` | 유효하면 `null` |
| `serialNo` | `PG-yyyy-6자리`(코스) · `HH-yyyy-6자리`(회향). **재발행되면 번호가 바뀐다** |

**완주가 취소됐다가 다시 성립할 때 인증서와 보상이 서로 다르게 움직인다.**

| | 취소될 때 | 다시 성립할 때 |
|---|---|---|
| 인증서 | `REVOKED` 로 남는다 | **새 번호로 재발행**. 옛 번호는 무효인 채 계속 조회된다 |
| 보상 | `GRANTED` 만 `REVOKED` | **같은 행이 `GRANTED` 로 복귀**(새 보상이 생기지 않는다) |

번호는 이미 밖으로 나갔을 수 있어 되살릴 수 없고, 보상은 나간 적이 없어 되살리는 것이 맞다.
화면에 인증서 번호를 캐시해 두었다면 목록을 다시 읽어야 한다.

**공개 진위 확인 `GET /api/certificates/verify/{serialNo}` 의 응답 필드는 일곱뿐이다.**

```json
{ "serialNo": "PG-2026-000012", "certType": "PILGRIMAGE", "status": "VALID",
  "courseName": "…", "holderMasked": "순*자", "issuedAt": "…", "revokedAt": null }
```

- **`holderNickname` → `holderMasked` 로 이름이 바뀌었고 `valid`(불리언)는 없어졌다.** `status` 를 보면 된다.
- **회수된 번호는 404 가 아니라 200 + `status: "REVOKED"`** 다. "그런 번호가 있었고 지금은 무효" 가 이 API 의 목적이다.
  없는 번호만 404 `CERT-4041`. 회수 사유는 응답에 싣지 않는다.
- userId·이메일·전화·주소는 담기지 않는다. 이 응답만 비로그인으로 나간다.

## 4-13. 회원 탈퇴 (챕터 1 보강)

```
DELETE /api/users/me      body { "password": "…" }      →  204 (본문 없음)
```

- **비밀번호를 다시 받는다.** 틀리면 로그인과 **같은 401 `AUTH-4011`** 이고, **5회 잠금도 똑같이** 걸린다
  (`AUTH-4031`). 탈퇴 화면에서도 잠금 안내 문구가 필요하다.
- 성공하면 **204 이고 본문이 없다.** 응답 봉투(`success`·`timestamp`)도 없다 — 이 API 만 예외다.
- **그 순간부터 옛 토큰이 전부 401 이다.** 액세스 토큰이 아직 안 만료됐어도 막힌다(서버가 매 요청 상태를 본다).
  탈퇴 응답을 받으면 저장해 둔 토큰·쿠키를 지우고 로그인 화면으로 보내면 된다.
- **같은 이메일로 다시 가입할 수 있다.** 단 **다른 사용자**다 — 새 `userId` 를 받고, 옛 기록은 따라오지 않는다.
  "복구" 가 아니라는 것을 화면에서 분명히 해야 한다.
- 탈퇴한 계정으로 로그인하면 **401 `AUTH-4011`**(가입 여부를 알려주지 않는 그 코드)이다.

**탈퇴 뒤에 남는 것과 사라지는 것** — 사용자에게 미리 알려야 하는 부분이다.

| 사라진다 | 남는다 |
|---|---|
| 사진·한 줄 문장·생각상자·명상 기록 | 완주 기록(집계용, 누구인지는 남지 않는다) |
| 배송 정보(이름·연락처·주소) | 인증서 **번호** — 단 `REVOKED` 로 바뀌고 이름 자리는 `탈퇴회원` |
| 이메일·닉네임·비밀번호·계층 | 동의 기록(법정 보존, 철회 시각이 찍힌다) |

- **공개 진위 확인은 계속 200 이다.** 제3자가 확인하던 번호라 사라지면 안 된다 —
  `status: "REVOKED"`, `holderMasked: "탈퇴회원"` 으로 나간다.
- 아직 지급되지 않은 보상은 회수되고, **이미 신청·지급된 실물은 그대로 둔 채 관리자 쪽에 표시만 켠다**
  (택배가 이미 나갔을 수 있다).

---

## 4-14. 원고 — 편집자·심사·CSV 반입 (챕터 8)

### 문 아홉 (권한이 두 갈래다)

| 문 | 권한 | 비고 |
|---|---|---|
| `POST /api/editor/manuscripts` | EDITOR·ADMIN | 201. `variantNo` 는 **서버가 매긴다** — 보내지 말 것 |
| `PUT /api/editor/manuscripts/{id}` | 작성자 본인 | 제목·본문만. 사찰·구·종류는 못 바꾼다(변형 키다) |
| `POST /api/editor/manuscripts/{id}/submit` | 작성자 본인 | |
| `GET /api/editor/manuscripts` | EDITOR·ADMIN | 편집자는 **자기 것만**, 관리자는 전체 |
| `GET /api/admin/manuscripts` | ADMIN | 심사 목록 |
| `POST /api/admin/manuscripts/{id}/approve` | ADMIN | 자기 원고는 못 한다 |
| `POST /api/admin/manuscripts/{id}/reject` | ADMIN | `reason` 필수 |
| `POST /api/admin/manuscripts/{id}/retire` | ADMIN | |
| `POST /api/admin/manuscripts/import` | ADMIN | multipart `file` · `?dryRun=true` |

**삭제 API 는 없다.** 잘못 들어온 원고도 퇴역시킬 뿐이다 — 이미 그 원고를 참조하는 도장이 있으면
지우는 순간 그 사람의 기록에서 글이 사라진다. 화면에도 "삭제" 대신 "퇴역" 이라고 적어야 한다.

### 상태 기계 — 화면이 그릴 것

```
DRAFT ──제출──▶ SUBMITTED ──승인──▶ APPROVED ──퇴역──▶ RETIRED
  ▲                  │
  └──수정──── REJECTED ◀──반려(사유 필수)
```

- 지금 상태에서 못 하는 일은 **전부 같은 409 `MS-4090`** 이다. 버튼을 상태로 가려서 그리면 된다.
- **남의 원고는 403 이 아니라 404 `MS-4040`** 이다 — 있다는 사실 자체가 정보다.
- 반려본을 수정하면 자동으로 DRAFT 로 돌아가고, **`rejectReason` 은 지워지지 않는다**(이력).
- 자기 원고를 자기가 심사하면 403 `MS-4030`.

### 등록할 때 걸리는 것

| 코드 | 언제 | 화면 문구 |
|---|---|---|
| `COMMON-4000` | 제목 50자 초과 · MISSION 본문 2,000자 · EXT 본문 200자 초과 · 빈 값 | 필드 오류로 온다 |
| `MS-4002` | 링크·전화번호·이메일·HTML 태그가 섞였다 | **지우지 않고 거부한다** |
| `MS-4091` | 같은 자리에 같은 본문이 이미 있다 | |
| `MS-4092` | 변형 상한 3(기본 원고는 1) | "먼저 퇴역시키세요" |

### 사용자 응답에 붙은 필드 둘

**둘 다 값이 없으면 통째로 `null` 이고, 필드 자체는 항상 있다**(`present:false` 와 같은 원칙).

```json
// POST /api/stamps/{id}/qr · GET /api/stamps/{id} · GET /api/stamps/by-slot/{courseSiteId}
"missionManuscript": { "manuscriptId": 42, "siteId": 12, "title": "…", "body": "…", "variantNo": 2 }

// POST /api/stamps/{id}/mission · GET /api/stamps/by-slot/{courseSiteId} · GET /api/passport (칸마다)
"extPhrase": { "title": "…", "body": "…", "variantNo": 1 }
```

- `missionManuscript.siteId` 가 `null` 이면 **기본 원고**를 받은 것이다(그 절 전용 원고가 아직 없다).
- 한 번 정해진 원고는 **그 세션·그 도장 안에서 바뀌지 않는다.** 관리자가 그 사이 원고를 퇴역시켜도
  읽던 글은 그대로다 — 새로 고쳐도 안 바뀌는 것이 정상이다.
- 그 절에도 기본에도 미션 원고가 없으면 QR 통과가 409 `MS-4093` 이다. 시더가 열 편을 넣으므로
  정상 운영에서는 나지 않는다.

### CSV 양식

```
site_name,sigungu,verse_no,kind,title,body
대원사,산청,1,MISSION,지리산 대원사에서 첫 걸음,"절 마당에 들어서기 전에 …"
```

- UTF-8(엑셀 BOM 허용) · 2 MB · 1,000행까지. 전부 **DRAFT** 로 들어간다(심사는 따로).
- `site_name` 이 비면 기본 원고. 있으면 **이름 완전 일치 + 시군구**가 둘 다 맞아야 한다
  (같은 이름의 절이 7이름 15곳 있다 — §6-9).
- **한 행이라도 틀리면 아무것도 넣지 않는다.** 응답은 400 이고 공통 봉투의 `error.fields` 에
  `{"field": "6행 site_name", "reason": "…"}` 형태로 최대 50건이 실린다.
- `?dryRun=true` 는 검증만 하고 `{ok, wouldInsert, inserted:0, errors}` 를 준다. 크게 올리기 전에 이걸 먼저 돌린다.
- 예시 10행: `backend/docs/content/manuscripts.csv`

---

## 4-15. 전자책 · 인증서 PDF · 인쇄주문 · 청소기 (챕터 9)

### 전자책 — 요청하고 기다렸다가 받는다

```
POST /api/ebooks           →  202 {ebookId, status:"REQUESTED"}   만들기 시작
                              200 {ebookId, status:"READY", ...}  같은 재료의 책이 이미 있다
GET  /api/ebooks           →  내 목록(최신순). downloadUrl 은 없다
GET  /api/ebooks/{id}      →  1권. status·pageCount·byteSize·failReason·downloadUrl
```

- **상태코드가 둘인 것이 핵심이다.** 202 면 잠시 뒤 다시 조회하고, 200 이면 바로 링크를 받으러 가면 된다.
  둘을 한 코드로 뭉치면 그 분기를 본문에서 다시 읽어야 한다.
- `downloadUrl` 은 **READY 일 때만** 채워지고 그 밖에는 `null` 이다 — **에러가 아니다.**
  "만드는 중" 도 답이라, 그때마다 4xx 를 띄우면 사용자가 오류 화면을 본다.
- 링크는 **10분**짜리 서명 URL 이다. 퍼 나른 링크는 열리지 않는다.
- **같은 재료면 같은 책**이다. 버튼을 여러 번 눌러도 책이 쌓이지 않는다.
  재료가 바뀌면(도장·사진·문장·생각상자·인증서가 늘거나 줄면) 새 책이 된다.
- 만들기까지 **최대 5분**(청소기 주기)이 걸린다. 화면에 "만드는 중" 을 두고 다시 조회하게 한다.

| 코드 | 언제 |
|---|---|
| 400 `EBOOK-4001` | 도장이 하나도 없다 — 만들 재료가 없다 |
| 409 `EBOOK-4092` | 이미 만드는 중인 책이 있다 |
| 429 `EBOOK-4290` | 하루 상한(기본 3권) |
| 404 `EBOOK-4040` | 없거나 **남의 책**(403 이 아니다) |

내 책은 **다섯 권까지** 남는다. 넘으면 가장 오래된 것이 사라진다 — 다만 **인쇄 주문이 걸린 책은 남는다.**

### 인증서 PDF

```
GET /api/certificates/{id} → 번호·종류·상태·코스명·발행일·회수일·verifyUrl·downloadUrl
```

- `downloadUrl` 은 **VALID 일 때만**. 회수(REVOKED)되면 `null` 이고 에러가 아니다.
- PDF 는 **처음 열 때 만들어진다.** 첫 조회가 조금 느릴 수 있다.
- 증서에는 **이름이 그대로** 찍힌다(본인만 받는 파일). 공개 진위 확인의 마스킹과 다르다.
- 회수된 뒤에도 **이미 내려받은 파일은 살아 있다.** 그 파일의 QR 을 찍으면 진위 확인이
  `REVOKED` 를 답한다 — **파일이 아니라 QR 이 가리키는 곳이 정본**이라고 안내해야 한다.

### 인쇄주문 — 상태가 여섯이다

```
REQUESTED ─확인─▶ CONFIRMED ─인쇄중─▶ PRINTING ─발송─▶ SHIPPED ─완료─▶ DONE
    │                  │
    │                  └─관리자 취소(사유 필수)─▶ CANCELED
    └─사용자 취소 DELETE /api/print-orders/{id} → 204 ─▶ CANCELED
```

- 사용자 취소는 **REQUESTED 에서만**. 그 뒤에는 409 `PRINT-4090` 이다. 취소해도 **행은 남는다.**
- 주문 대상은 **본인 READY 전자책**이다. 아니면 400 `PRINT-4001`.
  (옛 "회향본만" 제한은 없어졌다.)
- 같은 책으로 다시 신청하면 409 `PRINT-4091` — 표 밖 전이(4090)와 코드가 다르다.
- 부수는 1~5. 부수당 가격은 서버 설정값이고 **결제 연동은 아직 없다**(관리자 확인 = 입금 확인).
- **목록에는 배송정보가 없다.** `GET /api/print-orders` 의 항목에는 주소 계열 키가 아예 없고,
  `GET /api/print-orders/{id}` 에만 `shipping{recipient, phone, postalCode, address, memo}` 가 실린다.

### 관리자

| 문 | 하는 일 |
|---|---|
| `GET /api/admin/users?status=&q=&page=&size=` | 기본 **ACTIVE**. `?status=DELETED` 를 명시할 때만 탈퇴 계정. 해시·토큰·주소 없음 |
| `POST /api/admin/housekeeping/run` | 청소기 한 바퀴를 지금 돌린다. 응답에 `{orphanDeleted, orphanFailed, tokenDeleted, ebookReady, ebookFailed, sessionExpired}` |
| `PATCH /api/admin/ebooks/print-orders/{id}/status` | `{status, trackingNo?, reason?}` — 발송에는 송장, 취소에는 사유가 필수 |

---

