# 챕터 5 — 현장 인증 3단계

원문 `backend/docs/textbook/ch5.md` · 루트 `src/` · git 없음 · 교재 정본(원본은 `backend/docs/audit/ch5-replaced/`)
시작 상태: 챕터 4 마감 직후 — JUnit 108 · newman 420 · 실패 0

---

## 0. 정리.md §6 함정 5개 — 이 챕터에서 걸리는 자리

| # | 함정 | 챕터 5 에서 걸리는 자리 |
|---|---|---|
| 6-1 | **MySQL 은 `SET` 절을 왼쪽부터 평가한다** | `markCompleted`·`markPendingTravelTime` 의 `SET` 은 전부 상수·파라미터 대입이라 서로를 읽지 않는다 — 지금은 안전하다. 다만 나중에 `mission_verified_at` 을 읽어 다른 컬럼을 채우는 대입이 끼면 그 줄을 **읽는 쪽 먼저** 두어야 한다(교재 XML 주석에 이미 표시). |
| 6-2 | **`BusinessException` 은 롤백 대상이다** | **만료 경로가 정확히 이 함정이다.** `EXPIRED` 를 남기고 곧바로 409 를 던지므로, 같은 트랜잭션이면 기록이 함께 되돌아간다. 교재는 `StampExpireService` 를 `REQUIRES_NEW` 로 떼어 해결한다(챕터 1 의 `noRollbackFor` 와 목적은 같고 방법이 다르다). `@Transactional` 은 프록시라 **같은 클래스 내부 호출로는 안 걸린다** — 그래서 별도 클래스여야 한다. |
| 6-3 | **`LEFT JOIN` 조건을 `WHERE` 로 내리지 말 것** | `findPending`(관리자 심사 목록)이 `stamp LEFT JOIN site`·`LEFT JOIN users` 를 쓴다. 사찰이 아직 없는 EVIDENCE 건이 `WHERE` 로 내리는 순간 목록에서 사라진다 — 심사 대기가 조용히 비는 종류의 사고다. |
| 6-4 | **REPEATABLE READ 에서 재조회는 스냅샷을 본다** | `gpsCheck` → `pilgrimageService.ensureStarted` 가 이미 `FOR UPDATE` 로 고쳐져 있다(챕터 4). 이 챕터에서 새로 생기는 재조회는 `markQrDone` 0행 뒤의 상태 판정인데, **`findByIdForUpdate` 로 이미 잠근 행**을 쓰므로 스냅샷 문제가 없다. 잠그지 않은 `findById` 로 다시 읽으면 옛 값을 본다. |
| 6-5 | **검증 세트는 앞뒤로 정리해야 멱등하다** | 이 챕터가 `stamp`·`thinkbox`·`phrase_seen`·`task_seen`·`user_reward` 5개 표에 행을 남긴다. `cleanup.sql` 에 FK 역순(`user_reward → thinkbox → stamp → pilgrimage → seen`)으로 넣지 않으면 `run-all.sh` 를 두 번 돌렸을 때 "하루 5개"·"예외 2건" 한도가 먼저 걸려 뒷 요청이 전부 429 가 된다. |

---

## 1. 종단 테스트 먼저 — 교체 전 실측

### 1-a. 실측 결과 (지금 저장소 코드)

의견91.md 의 수기 확인 표를 `StampFlowIntegrationTest`(교재 기준) 로 옮겼다.
교재 기준 테스트는 첫 요청부터 400 `COMMON-4004` 로 막힌다 — 본문 `siteId` 가 아직 DTO 에 없기 때문이다.
그러면 "전부 실패" 한 줄이 되어 버그 목록이 안 되므로, **지금의 DTO·경로로 같은 시나리오를 한 번 더 훑어**
실제 응답을 찍었다(`LegacyStampBaselineProbe` — 표를 채운 뒤 지운다).

> **선행 조건 하나를 먼저 찾았다.** 처음 실행에서 1단계가 전부 `403 USER-4031`("위치기반서비스 이용에 동의해야…") 였다.
> **GPS 인증은 `LOCATION_SERVICE` 약관 동의가 선행 조건**인데 교재 [기본 47] `gpsCheck` 에는 그 검사가 없다.
> 교재로 그대로 갈아치우면 **동의 없이도 도장이 찍힌다.** → 예성 결정: **저장소 검사 유지**(§2-c).

| # | 시나리오 | 지금 저장소 | 교재 기대 | 판정 |
|---|---|---|---|---|
| T-03 | 정확도 LOW | 400 `STAMP-4001` | 400 | ✅ |
| T-04 | 반경 밖 | 400 `STAMP-4000` | 400 | ✅ |
| T-04′ | 정상 GPS | 200 GPS_DONE | 200 | ✅ (본문 `siteId` 없음 — v4 미반영) |
| T-04b | 그 자리의 후보가 아닌 사찰 | — (검사 없음) | 400 `COURSE-4001` | ❌ 미구현 |
| T-02 | QR 건너뛴 미션 | 409 `STAMP-4092` | 409 | ✅ |
| T-06a | 교재 경로 `POST /{stampId}/qr` | **404** | 200 | ❌ 저장소 경로는 `/qr-verify` |
| T-06b | `/qr-verify` + 쓰레기 토큰 | 400 `STAMP-4002` | 400 | ✅ |
| T-05 | 다른 사찰의 QR | 400 `STAMP-4004` | 400 | ✅ |
| T-08a | 정상 QR | 200 QR_DONE | 200 | ✅ |
| T-01 | 미션 제출 | 200 COMPLETED · `thinkbox(MISSION)` 1 · `task_seen` 1 · **`phrase_seen` 0** | `phrase_seen` 1 | ⚠ `expansionPhraseId` 미구현 |
| T-11 | 같은 자리 재시도 | 409 `STAMP-4090` "이미 완료된 스탬프입니다." | 409 + **사찰명·발행일** | ❌ 메시지에 정보 없음 |
| T-11b | `GET /by-slot/{courseSiteId}` | **404 `COMMON-4040`** | 200 | ❌ 미구현 |
| T-12 | 이동시간 미달 즉시 미션 | 200 **PENDING** · `pending_reason=TRAVEL_TIME` | 동일 | ✅ |
| T-08c | 61분 뒤 QR | 409 `STAMP-4091` · **DB `verify_status` = `GPS_DONE`** | DB = `EXPIRED` | ❌ **아래 참고** |
| T-06d | 회전 뒤 옛 QR | 400 `STAMP-4002` "교체된 QR 입니다." | 400 | ✅ |
| T-13 | 남의 스탬프 조회 | 403 `PILGRIMAGE-4030` | 403 | ✅ (코드 이름만 다름) |
| T-16 | 예외 접수 3건 | 200/200/200 (별도 확인) — **한도 없음** | 3번째 429 | ❌ 미구현 |
| T-09 | 하루 5개 뒤 여섯 번째 | 200 — **한도 없음** | 429 | ❌ 미구현 |

### 1-b. 저장소의 버그 목록 (실패한 것)

1. **만료가 기록되지 않는다 (가장 큰 것).** 저장소의 `StampExpireService` 는 교재의 `REQUIRES_NEW` 기록기가 아니라
   `@Scheduled(fixedDelay=PT5M)` **일괄 청소기**다. `StampService` 는 `STAMP_4091` 을 네 곳에서 던지면서
   `stampExpireService` 를 **한 번도 부르지 않고**, `StampMapper` 에는 `markExpired` 문장 자체가 없다
   (`expireStale` 만 있다). 결과: 409 를 받은 그 순간 DB 는 아직 `GPS_DONE` 이고, 최대 5분 동안
   "만료라고 응답했는데 상태는 진행 중" 인 창이 열린다.
   → 교재의 `markExpired` + `REQUIRES_NEW` 를 넣되 **청소기는 남긴다**(돌아오지 않는 세션은 청소기만 닫을 수 있다).
2. **하루 5개 한도가 없다** (`countCompletedToday` 미구현).
3. **예외 접수 하루 2건 한도가 없다** (`countEvidenceToday` 미구현).
4. **`GET /by-slot/{courseSiteId}` 가 없다** (Q1 ①).
5. **"이미 완료" 409 메시지에 사찰명·발행일이 없다** (Q1 ①).
6. **경로가 `/qr-verify` 다** — 명세·교재는 `/qr`.
7. **v4 미반영** — `GpsCheckRequest.siteId` 없음, `slot_site.exists` 검사 없음, `stamp.site_id` 미저장.
8. **`expansionPhraseId` 미반영** — `phrase_seen` 이 남지 않는다(`task_seen` 은 남는다).

### 1-c. 의미 12개 ↔ 실제 `ErrorCode` 대응표

교재 코드의 의미 이름을 아래 실제 상수로 바꿨다. **없던 3개는 명세 §7 표에 정의돼 있던 것이라
"신설" 이 아니라 "구현 누락" 이다** — 예성 결정으로 `ErrorCode` 에 넣었다(§2-d).

| 의미 | 교재의 이름 | 실제 상수 | 상태 | 비고 |
|---|---|---|---|---|
| 반경 밖 | `STAMP_4001` | **`STAMP_4000`** | 400 | 교재가 4001·4002 를 저장소와 **뒤바꿔** 썼다 |
| 정확도 LOW | `STAMP_4002` | **`STAMP_4001`** | 400 | 〃 |
| QR 무효 / 다른 사찰 / 구버전 | `STAMP_QR_INVALID` | `STAMP_4002`(형식·구버전) · `STAMP_4004`(다른 사찰) | 400 | 저장소가 더 잘게 나눠 놓았다. **유지** — "다른 사찰" 과 "형식 오류" 는 사용자가 할 일이 다르다 |
| QR 유효시간 | — | `STAMP_4003` | 400 | 토큰 자체의 `exp`. 교재 QR 토큰은 만료가 없어 지금은 안 쓰인다 |
| 순서 위반 | `STAMP_4092` | `STAMP_4092` | 409 | ✅ 그대로 |
| 이미 완료 | `STAMP_4091` | **`STAMP_4090`** | 409 | 교재가 4090·4091 도 뒤바꿔 썼다 |
| 세션 만료 | `STAMP_EXPIRED` | **`STAMP_4091`** | 409 | 〃 |
| 심사 중 | — | `STAMP_4093` | 409 | 저장소에만 있다. 유지 |
| 증빙 제출 불가 상태 | — | `STAMP_4094` | 409 | 저장소에만 있다. 유지 |
| 스탬프 없음 | — | `STAMP_4040` | 404 | |
| 자리 없음 | `COURSE_4042` | `COURSE_4042` | 404 | ✅ |
| 후보 아닌 사찰(v4) | `COURSE_4001` | `COURSE_4001` | 400 | 상수의 기본 메시지가 이 뜻에 안 맞아 **던질 때 메시지만 덮는다** — "이 자리의 후보 사찰이 아닙니다."(결정) |
| 미션 없음 | `VERSE_4041` | `VERSE_4041` | 404 | ✅ |
| 순례 없음 | `PILGRIM_4041` | **`PILGRIMAGE_4040`** | 404 | 이름만 다름 |
| **남의 스탬프(403)** | `STAMP_4031` | **`STAMP_4031` 추가** | 403 | STAMP 구역에 403 이 없었다. 명세 §7 근거로 추가(결정) |
| **하루 5개(429)** | `STAMP_4291` | **`STAMP_4291` 추가** | 429 | 〃 |
| **예외접수 하루 2건(429)** | `STAMP_4292` | **`STAMP_4292` 추가** | 429 | 〃 |
| 위치 동의 필요(403) | — | `USER_4031` | 403 | **교재에 없는데 저장소에 있다.** 지우지 않는다(결정) |

---

## 2. 교체 결과 — 무엇을 바꿨고 무엇을 남겼나

### 2-a. 교체 전 → 교체 후

| # | 시나리오 | 교체 전 | 교체 후 | |
|---|---|---|---|---|
| T-01 | GPS → QR → 미션 | 200 COMPLETED (`phrase_seen` 0) | 200 COMPLETED · `phrase_seen` 기록됨 | ✅ |
| T-02 | QR 건너뛴 미션 | 409 STAMP-4092 | 그대로 | ✅ |
| T-03 | 정확도 LOW | 400 STAMP-4001 | 그대로 | ✅ |
| T-04 | 반경 밖 | 400 STAMP-4000 | 그대로 | ✅ |
| T-04b | 후보 아닌 사찰 | 검사 없음 | **400 COURSE-4001** | 신규 |
| T-05 | 다른 사찰 QR | 400 STAMP-4004 | 그대로 (기준이 `stamp.site_id` 로 바뀜) | ✅ |
| T-06 | 경로 `/qr` | **404** | **200** (`/qr-verify` 제거) | 고침 |
| T-08 | 60분 뒤 QR | 409 · DB `GPS_DONE` | 409 · **DB `EXPIRED`** | 고침 |
| T-09 | 하루 5개 뒤 | 한도 없음 | **429 STAMP-4291** | 신규 |
| T-11 | 같은 자리 재시도 | 409 "이미 완료된 스탬프입니다." | 409 **"이미 발행된 스탬프입니다 — {사찰} · {날짜}"** | 고침 |
| T-11b | `GET /by-slot/{id}` | **404** | **200** (siteId·siteName·photoKey·userSentence) | 신규 |
| T-12 | 이동시간 미달 | 200 PENDING | 그대로 (기준이 `stamp.site_id` 로 바뀜) | ✅ |
| T-13 | 남의 스탬프 | 403 PILGRIMAGE-4030 | 403 **STAMP-4031** | 고침 |
| T-14 | 회전 뒤 옛 QR | 400 STAMP-4002 | 그대로 | ✅ |
| T-16 | 예외 접수 3건째 | 한도 없음 | **429 STAMP-4292** | 신규 |

`StampFlowIntegrationTest` **14건 전부 통과**. 교체 전에는 14건 중 12건이 첫 요청에서 막혔다.

### 2-b. ⚠ 교재 [기본 47] 의 결함 — 잠금과 REQUIRES_NEW 를 같은 행에 겹칠 수 없다

교재대로 `findByIdForUpdate` 로 도장 행을 잠근 뒤 만료 판정에서 `stampExpireService.expire()`
(`REQUIRES_NEW`)를 부르면, **안쪽 트랜잭션이 바깥 트랜잭션이 쥔 같은 행의 잠금을 기다리다 자기 자신과 교착한다.**
그대로 만들어 돌려 보니 60초 뒤 `Lock wait timeout exceeded` → **500** 이었다(테스트 T-08).

```
바깥 tx: SELECT ... FOR UPDATE (stamp 42)          ← 잠금 보유
안쪽 tx: UPDATE stamp SET ... WHERE stamp_id = 42   ← 영원히 대기
```

**고친 방식** — 만료 판정을 **잠그기 전으로** 옮겼다(`expireIfStale`). 잠금 전에는 두 트랜잭션이
같은 행을 두고 경쟁하지 않아 `REQUIRES_NEW` 가 정상 커밋된다.
이 검사를 통과한 뒤 잠금 안에서 만료되는 아주 좁은 창은 SQL 의 `gps_verified_at >= …` 조건이 막고,
그 행은 **청소기**(`expireStale`, 5분)가 닫는다. 그래서 두 경로가 함께 있어야 한다(예성 결정 [답 4]).

청소기 SQL 확인: `WHERE verify_status IN ('GPS_DONE','QR_DONE') AND gps_verified_at < NOW() - {session}분`
— GPS_DONE·QR_DONE 둘 다 대상이고 이미 `EXPIRED`·`COMPLETED`·`PENDING` 인 행은 건드리지 않는다. ✅

### 2-c. 교체·삭제·유지

**교체** — `StampController` 경로(`/qr-verify` → `/qr`, `by-slot` 추가) · `StampService` 3단계 전부 ·
`StampMapper`(+XML) 문장 6개 추가 · `StampExpireService` 에 즉시 기록기 추가 · DTO 3개.

**삭제** — `POST /api/stamps/{stampId}/qr-verify` (명세·교재의 `/qr` 로 통일).

**유지(교재에 없지만 지우지 않은 것)** — 교재를 그대로 따르면 사라졌을 저장소의 보호 장치들이다.

| 유지한 것 | 왜 |
|---|---|
| `userService.requireLocationAgreement` (403 `USER-4031`) | **위치기반서비스 동의 없이 위치 기반 도장이 찍힌다.** 동의는 지우면 안 되는 종류다 (예성 결정) |
| `storageClient.verifyOwnedKey` + `photoService.register` | 교재는 키 접두어 문자열만 본다. 실제 소유·용도 확인이 사라진다 |
| `findOrCreate` — 만료·반려 행 재사용 | 교재는 매번 INSERT 다. 같은 자리를 재시도할 때마다 행이 쌓인다 |
| `STAMP_4093`(심사 중) · `STAMP_4094`(증빙 불가 상태) | 교재에 없다. 보류 중인 자리에 다시 손대는 경로를 막는다 |
| `getPending` · `review` (관리자 심사) | 교재는 "같은 규칙으로 작성" 이라고만 했다. 이미 있고 잘 돈다 |
| `@Scheduled` 청소기 | 위 §2-b |

### 2-d. [추가 코드] — 명세 §7 근거, 신설이 아니라 구현 누락

| 상수 | status · code · message | 쓰이는 곳 |
|---|---|---|
| `STAMP_4031` | 403 · `STAMP-4031` · 본인의 스탬프가 아닙니다. | `requireOwner` |
| `STAMP_4291` | 429 · `STAMP-4291` · 오늘 받을 수 있는 스탬프를 모두 받았습니다. | `gpsCheck` — `countActiveToday` 기준(§5-3) |
| `STAMP_4292` | 429 · `STAMP-4292` · 예외 접수는 하루 2건까지입니다. | `submitEvidence` |

생성자 순서 `(status, code, message)` 를 지켰다. `COURSE_4001` 은 코드를 그대로 두고
`"이 자리의 후보 사찰이 아닙니다."` 로 메시지만 덮었다(예성 결정).

설정값도 함께 뺐다 — `application.yml` 의 `stamp.daily-limit: 5` · `stamp.evidence-daily-limit: 2`.
코드에 5·2 를 박으면 운영에서 조정할 방법이 없다.

---

## 3. 검사표 (STEP 1-c)

| 파일 | 근거 | 교재와의 차이 | 판정 |
|---|---|---|---|
| `QrTokenProvider` | [기본 44] | 저장소는 `exp`(선택)를 넣을 수 있다. 교재는 만료 없음(회전만) | **저장소 유지** — 발급 API 가 `validitySeconds` 를 안 받으면 만료 없이 나간다. 인쇄물·표시기 둘 다 되는 쪽이 넓다 |
| `StampMapper`(+XML) | [기본 45] | 문장 이름이 다르다(`save`/`markGpsDone` ↔ `insertGpsDone`) | **저장소 이름 유지 + 교재 문장 6개 추가**. 이름만 바꾸면 호출부 전체가 흔들린다 |
| `StampExpireService` | [기본 46] | 저장소는 청소기, 교재는 즉시 기록기 | **둘 다** (§2-b) |
| `StampService` | [기본 47] | 위 §2-c 표 | **교재 구조 + 저장소 보호 장치** |
| `StampController` | [기본 48] | 경로 `/qr-verify`, `by-slot` 없음 | **교재로 교체** |
| `AdminStampController` | [기본 48] | 이미 `pending`·`review` 를 갖췄다 | **유지** (전문은 §6) |
| `AdminSiteController` QR | [기본 48] | `pngBase64` ↔ 저장소 `qrImageBase64` | **저장소 이름 유지** — 이미 프론트에 나간 이름이고 뜻이 더 분명하다 |
| `PhraseService` | 챕터 2 이월 | `validatePhraseFor` 없음 | **추가** (+`ExpansionPhraseMapper.existsForVerseAndTier`) |
| `ThinkboxService.createFromMission` | — | 인자 순서만 다름 | **저장소 유지**, 사찰만 `stamp.site_id` 로 |
| `SiteDistanceMapper.findMinMinutes` | — | 이미 있음 | **유지** |

원본 10개는 `backend/docs/audit/ch5-replaced/` 에 그대로 있다.

---

## 4. 테스트·검증

```
JUnit   127/127   (챕터 시작 108 → +19)
newman  요청 132/132 · 단언 578/578 · 실패 0   ← 2회 연속 같은 값(멱등)
SQL     채점 9절 전부 기대값
```

| 파일 | 건수 | 무엇 |
|---|---:|---|
| `StampFlowIntegrationTest` (신규) | 18 | 의견91.md 수기 표 전부 — 3단계·순서·만료·이동시간·한도 3종·증빙 후보검증·권한 |
| `StampLockOrderIntegrationTest` (신규) | 1 | 사용자 미션 제출과 관리자 승인을 **실제 두 스레드로** 동시에 — 교착 없이 둘 다 200 |

컬렉션 폴더 **`T 스탬프 3단계 (챕터 5)`** 31요청을 `P` 다음에 넣었다.
`cleanup.sql` 에 `user_reward → thinkbox → stamp → phrase_seen → task_seen` 정리와
`UPDATE site SET qr_version = 1` (T14 회전 되돌리기)을 추가했다 — 이게 없으면 2회차가 한도·구버전에 걸린다.

**T 폴더가 코스 1(데모)을 쓴다** — 지시문은 "서울 시드 코스를 ACTIVE 로 전환" 이었으나, 시드 사찰 110곳은
`qr_location_hint` 가 비어 있어 ACTIVE 로 못 올라간다(`ADMIN-4092`). 5곳에 힌트를 넣고 코스를 올린 뒤
`cleanup.sql` 로 되돌리는 경로는 중간에 끊기면 시드가 오염된 채 남는다. 코스 1 은 이미 ACTIVE 이고
`data.sql` 이 관리하므로 이쪽이 안전하다. 대신 `data.sql` 에 **코스 1 자리마다 `slot_site` 대표 1행**을
넣었다 — 없으면 v4 후보 검사가 `COURSE-4001` 로 막는다.

---

## 5. [질문] — 2026-09-06 전부 확정

### 1. QR 유효기간 — **없음** 확정

QR 은 인쇄물이라 만료 대신 **회전**(`qr_version + 1`)으로만 무효화한다. 발급 API 의 `validitySeconds` 를
주지 않으면 만료 없는 토큰이 나가므로 지금 동작이 곧 확정안이다.
`STAMP_4003` 은 **지우지 않고** `ErrorCode` 에 주석으로 남겼다 —
"미사용(예약) — QR 은 만료 없음, 회전으로 무효화". 정리.md §3 원칙에도 한 줄 넣었다.

### 2. 하루 한도 기준 — **서버 시간대(Asia/Seoul) 자정**, `CURDATE()` 유지

| 자리 | 설정 | 확인 |
|---|---|---|
| JDBC | `.env` 의 `DB_URL` 에 `serverTimezone=Asia/Seoul` | ✅ |
| Jackson | `JacksonConfig.TIME_ZONE = "Asia/Seoul"` · `application.yml` `spring.jackson.time-zone: Asia/Seoul` | ✅ |
| **MySQL 서버** | `@@global.time_zone` = `SYSTEM` · `@@session.time_zone` = `SYSTEM` | ⚠ 아래 |

**⚠ 주의 — `CURDATE()`·`NOW()` 는 MySQL 서버가 평가한다.** JDBC 의 `serverTimezone` 은 드라이버가
값을 <b>해석</b>하는 기준일 뿐, 서버의 `time_zone` 을 바꾸지 않는다. 지금은 `SYSTEM` 이고 이 장비 OS 가
KST 라 결과적으로 Asia/Seoul 자정이 맞다. **OS 시계가 UTC 인 서버에 올리면 한도 초기화 시각이 오전 9시로 밀린다.**
→ 배포 체크리스트에 넣었다(정리.md §5-2).

### 3. 관리자 승인과 한도 — **한도는 사용자 요청 시점에서만**, 다만 세는 기준을 바꿨다

승인은 관리자의 행동이라 한도를 걸지 않는다. 대신 `countCompletedToday` → **`countActiveToday`** 로 바꿨다.

```sql
WHERE p.user_id = #{userId}
  AND st.created_at >= CURDATE()
  AND st.verify_status NOT IN ('EXPIRED', 'REJECTED')   -- GPS_DONE·QR_DONE·COMPLETED·PENDING 포함
```

COMPLETED 만 세면 **이동시간 미달로 PENDING 에 빠진 건이 한도에서 빠져나가고**, 그것이 나중에 승인되면
그날 완료가 6개가 된다. EXPIRED·REJECTED 만 빼는 이유는 그 둘은 사용자가 다시 시도해야 하는 실패한 시도라서다.

테스트 3건 — **T-09b**(PENDING 4 + 진행 중 1 → 여섯 번째 429) · **T-09c**(EXPIRED 3 + REJECTED 1 + COMPLETED 1 → 200) ·
기존 T-09(COMPLETED 5 → 429).

### 4. EVIDENCE 의 `site_id` — **`EvidenceRequest.siteId` 필수**

`@NotNull @Positive` 이고 `slot_site.exists` 로 GPS 경로와 **같은 후보 검증**을 받는다(아니면 400 `COURSE-4001`).
`insertEvidence`(=`save`)와 `markPending` 이 `site_id` 를 저장하므로, 승인 뒤에도 이동시간 검사와
여권의 `siteName` 이 맞는다. 컬렉션 T16 세 요청의 본문도 함께 고쳤다.

테스트 2건 — **T-16b**(후보 아닌 사찰 → 400) · **T-16c**(접수한 도장에 `site_id` 가 남는다).

---

## 6. `AdminStampController` 전문 (유지 — 교체하지 않음)

```java
// src/main/java/com/templestamp/admin/AdminStampController.java
@Validated
@RestController
@RequestMapping("/api/admin/stamps")
@RequiredArgsConstructor
public class AdminStampController {

    private final StampService stampService;

    @GetMapping("/pending")
    public ApiResponse<PageResponse<PendingStampResponse>> getPending(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(stampService.getPending(page, size));
    }

    /** APPROVE → COMPLETED 확정 + 완주 재집계 / REJECT → REJECTED 종단(사유 필수). */
    @PostMapping("/{stampId}/review")
    public ApiResponse<Void> review(@AuthenticationPrincipal AuthenticatedUser admin,
                                    @PathVariable Long stampId,
                                    @Valid @RequestBody StampReviewRequest request) {
        stampService.review(admin.userId(), stampId, request);
        return ApiResponse.ok();
    }
}
```

QR 발급·회전은 `AdminSiteController` 에 있다 — `POST /api/admin/sites/{siteId}/qr`(선택 `validitySeconds`,
응답에 `qrToken`·`qrImageBase64`·`qrVersion`) · `POST /api/admin/sites/{siteId}/qr/rotate`(버전 +1).
PNG 는 zxing `ErrorCorrectionLevel.H` — 사찰 밖에 붙는 인쇄물이라 젖거나 긁혀도 읽히도록 복원 수준을 높게 잡았다.

---

**newman 578/578, 기능 ✅ 10 · ⚠ 0 · ❌ 0 · — 1**

- ✅ 3단계 전 경로 · v4 후보 인증(GPS·증빙 둘 다) · 만료 즉시 기록 · 하루 한도 2종 · by-slot · 409 메시지 ·
  잠금 순서 · T 폴더 31요청 · QR 만료 없음 확정 · 한도를 살아 있는 도장 기준으로
- ⚠ 없음 — 교재 [기본 47] 의 잠금 ↔ REQUIRES_NEW 겹침은 저장소에서 고쳤고 `ch5.md` [기본 46] 에
  교재 보완으로 적었다(§2-b)
- — MySQL 서버의 `time_zone` 이 `SYSTEM` 이라 **OS 시계가 UTC 인 서버에서는 하루 한도 초기화가 9시간 밀린다**(§5-2).
  코드가 아니라 배포 환경의 문제라 정리.md §5-2 에 넣었다
