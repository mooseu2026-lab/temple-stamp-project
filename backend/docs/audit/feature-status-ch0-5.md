# 중간 점검 B — 챕터 0~5 전체: 기능 · DB 연결 · API 69개

원문 `backend/docs/textbook/verify-ch0-5.md` · 실행 2026-09-06 · 전제 `audit/ch5-hardening.md` ⚠ 0 (확인함)
**목적은 점검·보고다.** 코드는 컬렉션·스크립트만 고쳤고, 발견한 결함은 고치지 않고 표에 적었다.

```
JUnit    133/133
newman   요청 168/168 · 단언 741/741 · 실패 0     ← 2회 연속 같은 값
DB       H1~H10 전부 통과 (H3 은 10분 실제 대기)
컬렉션   A(16) U(10) P(10) T(31) R(30) M(18) G(6) K(4) S·C(20) = 145 요청 / 168 실행
좌표     시드 사찰 110곳 중 미확보 18 → 1 (봉인사만 남음)
```

---

## 1. 기능 34행

| # | 기능 | 근거 | 결과 |
|---|---|---|---|
| **인증·계정** | | | |
| 1 | 가입 · 비밀번호 규칙 · 중복 이메일 | A01 201 / A02 409 `AUTH-4090` / A03 400 `fields[password]` | ✅ |
| 2 | 로그인 · 없는 계정과 같은 문구 | A04 `expiresIn 1800` · A05·A06 둘 다 401 `AUTH-4011` 동일 문구 | ✅ |
| 3 | 5회 실패 잠금 · 맞는 비밀번호도 403 | A07x 루프 → A08 403 `AUTH-4031` · SQL `login_fail_count=5, locked=1` | ✅ |
| 4 | refresh 회전 · 재사용 탐지 · `/reissue` 404 | A09·A10 회전 · A12 401 `AUTH-4015` · A13 최신 쿠키도 401 · A11 404 · SQL `alive=0` | ✅ |
| 5 | 로그아웃(전 기기·비로그인 멱등) | A14 `Max-Age=0` · A15 401 | ✅ |
| **회원** | | | |
| 6 | 내 정보 · 내부 필드 비노출 · 잘못된 tier | U01 401 · U02 비노출 · U04 400 | ✅ |
| 7 | 약관 배열 POST · 재동의 멱등 · DELETE 경로변수 | U05~U07 · **U08a/b/c** 200 → 0건 → 재동의 200 | ✅ |
| **마스터** | | | |
| 8 | 권역 10 · 코스 있는 권역 9 · code | M01 (수를 박지 않고 데이터 기준) · JUnit `nine_regions_carry_courses` | ✅ |
| 9 | 코스 목록/상세 · candidates · RIDER 정렬 · congested | M02~M05 · **M18** 자리 5칸 전부 후보 ≥1, SUNROAD 가 있는 자리는 그것이 첫 번째 | ✅ |
| 10 | 사찰 기본/8블록/가는 법 7칸 · 언어 4단 · tier 덮기 | M06~M17 (M17 7칸·조계사 기대 문장 고정) | ✅ |
| 11 | 내려간 코스·사찰 404 | C06 404 `COURSE-4041` · S 폴더 DRAFT 사찰 404 `SITE-4040` | ✅ |
| 12 | 관리자 사찰·코스·요소·뷰포인트·뱃지·이동시간 · 본문 status 400 | S01~S11 · C01~C08 (S03 UPSERT · S10/S10b 요소 2행 · C07 20행) | ✅ |
| 13 | 카카오 검색 | K01 400 · K02 200 `places[0]` · K03 400 | ✅ |
| **순례·여권** | | | |
| 14 | 시작 멱등 · 비활성 409 · 여권 3단 · 진행률 출처 통일 | P03 `created true` → P04 `false` 같은 id · P05 409 · P08 목록 진행률 일치 | ✅ |
| **시드·후보** | | | |
| 15 | 사찰 110 · 코스 12 · 자리 60 · 후보 213 · 요소 342 · 멱등 | D01 `seed-v4-check.sql ①` — `slot_site` 218 = 시드 213 + 데모 5 | ✅ |
| 16 | 동명이찰 분리 · 좌표 0 목록 · 미확인 DRAFT | D02 `seed_key` 유일 · D03 **좌표 0 이 18 → 1곳으로 줄었다**(§4) · 시드 110곳 전부 DRAFT | ✅ |
| **현장 인증** | | | |
| 17 | GPS 후보 검증·LOW·반경 밖·위치동의·이미 발행 | T02 `STAMP-4001` · T03 `STAMP-4000` · T04b `COURSE-4001` · T11 409 + 사찰명·발행일 | ✅ |
| 18 | QR 위조·다른 사찰·구버전 400 · 정상 QR_DONE | T06 `STAMP-4002` · T07 `STAMP-4004` · T08 200 · T14 구버전 400 | ✅ |
| 19 | 미션 길이 · COMPLETED · 보상 · 진행률 · seen 기록 | T09 400 · T10 COMPLETED·진행률 1·보상 ≥1 · SQL `site_elements` 채점 | ✅ |
| 20 | 이동시간 PENDING · 관리자 승인 · 여권 반영 | T12 PENDING·보상 [] · T15 승인 → T15b `completedCount 2` | ✅ |
| 21 | 60분 만료 → 409 + DB EXPIRED(잠금 앞) · 청소기 | JUnit `T-08` — `Lock wait` 없이 409 + DB `EXPIRED` | ✅ |
| 22 | 하루 5개(살아 있는 도장)·예외 2건·어제 행 재사용 | JUnit `T-09`·`T-09b`·`T-09c`·`B-3` · T16 429 `STAMP-4292` | ✅ |
| 23 | by-slot 조회 · 남의 스탬프 403 | T11b siteId·siteName·userSentence · T13 403 `STAMP-4031` | ✅ |
| 24 | **회향 하한** | **D05** — `active_courses 1 · hoehyang_cert 0 · hoehyang_ebook 0 · hoehyang_reward 0` | ✅ |
| **이후 챕터 코드 스모크 (챕터 6~9)** | | | |
| 25 | 생각상자 CRUD · is_edited | R01 201 · R02 · R03 200 → **R03b `isEdited true`** · R04 200 | ✅ |
| 26 | 명상 목록/상세(비로그인) · 재생 기록 | R05·R06 200 (비로그인) · R07 200 | ✅ |
| 27 | 보상 목록 · 수령 신청 | R08 ≥1건 · **R09 200** (§5-1) | ⚠ |
| 28 | 인증서 목록 · 진위 확인(마스킹) | R10 0건 · R11 404 `CERT-4041` — **5칸을 다 채우지 않아 인증서가 없다**(§5-2) | ⚠ |
| 29 | 전자책 목록 · 다운로드 URL · 인쇄 신청/조회/취소 | R12 0건 · R13 404 `EBOOK-4040` · R14 404 · R15 200 0건 (§5-2) | ⚠ |
| 30 | 업로드 presign(purpose 검증) | R16 `PHOTO/{userId}/…` · R17 400 | ✅ |
| 31 | 콘텐츠 예절 안내 · i18n | R18 200 · R19a 200 · R19b `i18n/xx` → 200 ko 폴백 — **명세 F-20 대로 확정**(§5-3) | ✅ |
| 32 | 관리자 보상 심사·전자책 파일·주문 상태·원고 등록 · USER 403 | R20 · R21 · R22a/R22b · R23 · R24 · R25 · R26 · **R27 403 `AUTH-4032`** | ⚠ |
| **규약·보안·배포** | | | |
| 33 | 좌표 차단 · ADMIN 분리 · 없는 보호 URL 401 · `/env` 401 · `/health` UP | G01 400 `COMMON-4001` · G02 403 · **G05 401 `AUTH-4013`** · G04 401 · G03 UP | ✅ |
| 34 | `X-Request-Id` 32자 · 클라이언트 값 유지 · timestamp +09:00 | 전 요청 공통 Tests · **G06** 보낸 값 그대로 | ✅ |

**✅ 30 · ⚠ 4 · ❌ 0 · — 0**
⚠ 4건은 전부 **챕터 6~9 범위**다(§5). 챕터 0~5 범위에서 실패한 것은 없다.

> **— (범위 밖)**: F-01 인트로(서버 없음) · 실제 S3 · 실제 전자책 조판. 이번 점검에서 다루지 않았다.

---

## 2. DB 연결 지속성 H1~H10

원본 `docs/verify/db-check-result.txt` · 스크립트 `docs/verify/db-check.sh`

| # | 검사 | 실측 | 결과 |
|---|---|---|---|
| H1 | 기동 직후 연결 | `/health` 200 UP · `SELECT 1` 왕복 | ✅ |
| H2 | 커넥션 누수 | newman 전 `Threads_connected 13` → 후 **13** (차이 0) | ✅ |
| H3 | **10분 유휴 후 재연결** | 14:56:52 → 15:06:53 대기 후 `/api/regions` `/api/courses/1` `/health` 전부 200 | ✅ |
| | | MySQL `wait_timeout` **28800초(8시간)** · HikariCP `maxLifetime` **기본 1800000ms(30분)** — 풀이 먼저 버린다(올바른 방향) | |
| H4 | 트랜잭션 잔류 | `INNODB_TRX` **0행** | ✅ |
| H5 | 잠금 대기·교착 | `LATEST DETECTED DEADLOCK` 없음 · `data_lock_waits` 0 | ✅ |
| H6 | 시간대 | `global/session = SYSTEM`, `NOW()` 가 호스트 시각과 일치(KST) | ✅ ※ |
| H7 | 외래키 무결성 | `stamp.site_id`·`slot_site`·`course_site` 고아 0 · `completed_course_site_id` 고아 0 | ✅ |
| H8 | 문자셋 | `utf8mb4` / `utf8mb4_unicode_ci` · 한글 왕복 "관리자"·"확인포인트"(5자) 그대로 | ✅ |
| H9 | `sql.init` 멱등 | 표 31 · 시드 사찰 110 · 코스 12 · `slot_site` 218 · 요소 342 · region 10 — 재기동해도 동일 | ✅ |
| H10 | 격리·잠금 순서 | JUnit `StampLockOrderIntegrationTest`(제출 vs 승인 동시) · `PilgrimageService` 시작 멱등(`FOR UPDATE`) | ✅ |

**H10/10.**

※ H6 — `SYSTEM` 은 **OS 시계를 따른다**는 뜻이다. 이 장비는 KST 라 결과가 맞지만
**OS 가 UTC 인 서버에 올리면 하루 한도(`CURDATE()`) 초기화가 오전 9시로 밀린다.**
코드가 아니라 배포 환경의 문제라 정리.md §5-2 9번에 있다.

---

## 3. API 69개 ↔ 폴더 대조 — 누락 0

| 구간 | API | 폴더 | |
|---|---|---|---|
| 1~4 | auth signup·login·refresh·logout | A01~A15 | ✅ |
| 5 | regions | M01 · G06 | ✅ |
| 6~7 | courses · /{id} | M02~M05 · M18 · T00c · T15b | ✅ |
| 8~10 | sites/{id} · /page · /guide | M06~M17 | ✅ |
| 11~12 | verses · /{verseNo} | M14~M16 | ✅ |
| 13~14 | meditations · /{id} | R05 · R06 | ✅ |
| 15~16 | content/guide · i18n/{lang} | R18 · R19a · R19b | ✅ |
| 17 | certificates/verify/{serial} | R11 | ✅ |
| 18 | health | G03 | ✅ |
| 19~23 | users/me (GET·PATCH) · agreements (GET·POST·DELETE) | U01~U08c · T00b | ✅ |
| 24 | passport | P01·P02·P07 | ✅ |
| 25 | pilgrimages POST | P03~P06 | ✅ |
| 26~31 | stamps gps-check·qr·mission·evidence·GET·by-slot | T02~T16 | ✅ |
| 32~35 | thinkbox POST·GET·PATCH·DELETE | R01~R04 | ✅ |
| 36 | meditations/logs | R07 | ✅ |
| 37~38 | rewards GET · claim | R08 · R09 | ✅ |
| 39 | certificates GET | R10 | ✅ |
| 40~41 | ebooks GET · download-url | R12 · R13 | ✅ |
| 42~45 | print-orders POST·GET·GET/{id}·DELETE | R14 · R15 | ⚠ **/{id}·DELETE 는 미실행** |
| 46 | uploads/presign | R16 · R17 | ✅ |
| 47~53 | admin/sites GET·POST·PUT·PATCH·viewpoints·badges·elements | S01~S11 | ✅ |
| 54~55 | admin/sites/{id}/qr · rotate | T01 · T06b · T12a · T14a · T14b | ✅ |
| 56~58 | admin/courses POST·PUT·PATCH | C01~C06 | ✅ |
| 59 | admin/site-distances | C07 | ✅ |
| 60~61 | admin/stamps pending · review | T15a · T15 | ✅ |
| 62~63 | admin/rewards claims pending · review | R20 · R21 · R27 | ✅ |
| 64 | admin/kakao/places | K01~K03 | ✅ |
| 65~66 | admin/contents phrases · missions | R22a · R22b · R23 | ✅ |
| 67~69 | admin/ebooks files · print-orders · status | R24 · R25 · R26 | ✅ |

**69개 중 67개가 컬렉션에 있다.** 빠진 둘은 `GET /api/print-orders/{id}` 와
`DELETE /api/print-orders/{id}` — **R14 인쇄 신청이 404 로 끝나 주문 id 가 생기지 않아서** 실행할 수 없었다.
회향본이 만들어지는 챕터 9 에서 함께 넣는다(§5-2).

---

## 4. 좌표 (0,0) 사찰 — 18곳 → **1곳** (콘텐츠 트랙 1)

스크립트 `docs/verify/coord-backfill.js` · 결과 `docs/verify/coord-backfill-result.json`
상세 표는 **`audit/seed-v4.md` §3-2**. 반영은 관리자 `PUT` 을 거쳤다.

**받아들이는 기준 — 이름 완전일치 > 시·군·구 일치 > 카테고리**
검색 순서는 ① 도로명 주소 ② 이름 + 시군구 + 읍면동 ③ 이름 + 구분 ④ 이름만.

### 두 번 걸러야 했다

| # | 처음 규칙 | 무슨 일이 났나 | 고친 것 |
|---|---|---|---|
| 1 | 카테고리에 "사찰" 이 있으면 채택 | 18곳이 전부 채워졌는데 **6곳 이상이 다른 고장의 동명이찰**이었다 — 화암사→완주, 백련사→부산, 용문사→양평, 흥국사→여수, 봉인사→진주, 학림사→서울 | 조사 자료의 주소·구분에서 **시·군·구**를 뽑아 결과 주소와 대조. 필수 조건으로 |
| 2 | 지역만 맞으면 채택 | **다솔사가 산내암자 봉일암(424)** 으로 잡혔다. 본사(417)는 카테고리가 `불교`(사찰 아님)라 밀렸다 | **이름 완전일치를 1순위로.** 종단 접두어를 떼고 비교 → `대한불교조계종다솔사`(417) 채택. 같은 규칙이 "흥국사 주차장"·"학림사 템플스테이"도 밀어낸다 |

### 결과

| 구분 | 곳 |
|---|---|
| 좌표 확보 | **17** (§seed-v4.md 3-2 표) |
| 규칙 ②로 바로잡힌 곳 | **1** — 다솔사 봉일암(424) → 본사(417) |
| "이름+행정구역" 조합으로 새로 잡힌 곳 | **2** — 학림사(공주 반포면) · 흥국사(고양 덕양구) |
| 여전히 0 | **1** — 봉인사 |

### 봉인사 — 답사 대상

`봉인사 남양주` · `남양주 봉인사` · `봉인사 진건읍` · 도로명 주소 `사릉로156번길 295` — **전부 0건**이다.
주소가 정확한데도 나오지 않는다. **카카오 장소 DB 에 그 절이 없다.** 이름만 쓰면 서울 용산(사리탑)과
경남 진주가 잡혀 지역 대조에서 걸린다. 현장에서 GPS 로 찍어 오는 수밖에 없다.

> 답사 우선순위는 **흥국사(고양)** 가 1순위였다 — 경기 북부·인천 라인의 유일한 후보라 그 자리가 비면
> 코스 한 칸이 통째로 막힌다. 이번에 좌표를 확보해 그 걱정은 없어졌고, 남은 것은 봉인사 한 곳이다.

## 5. 챕터 6~9 착수 시 수정 (R 폴더에서 나온 것)

**여기 있는 것은 이번에 고치지 않았다.** 5xx 는 하나도 없다 — 전부 4xx 이거나 동작 차이다.

| # | 무엇 | 실측 | 왜 지금 안 고치나 |
|---|---|---|---|
| 5-1 | **보상 수령 신청이 종류를 가리지 않는다** | `R09 POST /api/rewards/{id}/claim` → **200**. `claimable:false` 인 도장 보상도 신청이 통과한다 | **[챕터 7 반영 확정]** 응답의 `claimable` 을 믿지 말고 서버가 다시 검사한다 — `rewardType = PHYSICAL` **AND** `status = GRANTED` **AND** 본인 소유. 어긋나면 400 `REWARD-4001` / 409 `REWARD-4092`. 챕터 7 STEP 에 넣는다 |
| 5-2 | **인증서·전자책·인쇄가 전부 빈 상태로만 확인됨** | R10 0건 · R12 0건 · R13 404 `EBOOK-4040` · R14 404 | T 폴더가 5칸 중 2칸만 채워 완주가 일어나지 않는다. **`GET/DELETE /api/print-orders/{id}` 2개가 미실행으로 남은 것도 이 때문**(§3) |
| ~~5-3~~ | ~~`i18n/{lang}` 폴백~~ | `GET /api/content/i18n/xx` → 200 `{"lang":"ko",…}` | **확정 — 유지**(명세 F-20 "미번역 시 ko 폴백"). 어느 언어가 나갔는지는 응답의 `lang` 이 알려 준다. 정리.md §4-6 에 한 줄 넣었다 |
| 5-4 | **확장문구와 미션의 번호 상한이 다르다** | 확장문구 `versionNo` 는 `@Min(1) @Max(5)`, 미션 `variantNo` 는 **상한 없음**(99 도 통과) | 원고 관리 규칙(챕터 8)에서 함께 정한다 |
| 5-5 | **인쇄 주문 상태에 `PRINTING` 이 없다** | `PATCH …/status` 는 `CONFIRMED·SHIPPED·CANCELED` 만 받는다 | 조판·인쇄 흐름(챕터 9)에서 상태 목록을 확정한다 |

### 컬렉션에서 고친 것 (내 쪽 오류)

| 무엇 | 원인 |
|---|---|
| R03 이 `isEdited` 를 PATCH 응답에서 읽으려 했다 | `PATCH /api/thinkbox/{id}` 는 `ApiResponse<Void>` — 본문이 없다. **R03b** 로 다시 읽어 확인하도록 나눴다 |
| R22 `versionNo: 99` | 확장문구는 1~5. 빈 자리 2 로 바꾸고, 범위 밖 400 은 **R22b** 로 따로 남겼다 |
| R26 `status: PRINTING` | 없는 값이었다. `CONFIRMED` 로 바꿨다 |
| `coord-backfill.js` 가 CSV 대조에 계속 실패 | **윈도우 `mysql` 이 줄 끝에 `\r` 을 붙인다.** `.trim()` 은 마지막 줄만 지워, 18곳 중 17곳이 조용히 빗나갔다(§6) |

---

## 6. [질문] — 2026-09-06 답 반영

| # | 질의 | 답 | 이번에 한 것 |
|---|---|---|---|
| Q-a | `i18n/{lang}` 폴백 | **유지**(명세 F-20). 없는 언어는 404 가 아니라 ko 본문, 어느 언어가 나갔는지는 응답이 표시 | 정리.md §4-6 에 한 줄. **코드 변경 없음** — 응답의 `lang` 필드가 이미 그 역할을 한다 |
| Q-b | 보상 `claimable` 서버 재확인 | **필수**. `PHYSICAL` + `GRANTED` + 본인 소유를 다시 검사, 아니면 400 `REWARD-4001` / 409 `REWARD-4092` | **[챕터 7 반영]** 표(§5-1)에 기록만. 지금은 고치지 않았다 |
| Q-c | 다솔사 좌표 | **봉일암 반려.** 이름 완전일치 > 시군구 > 카테고리 규칙으로 본사를 잡을 것 | 스크립트에 규칙을 넣고 15곳을 다시 돌렸다 — **바뀐 곳은 다솔사 한 곳**(424 → 417). §4 |
| Q-d | 남은 3곳 | "이름 + 시군구 + 읍면동" 으로 한 번 더, 그래도 0 이면 답사 목록 | **학림사·흥국사 확보**, 봉인사만 남았다. 흥국사(고양)는 경기 북부 라인의 유일한 후보라 1순위였는데 이번에 해결됐다. §4 |

### 남은 [질문] — 없음

이번 점검에서 새로 생긴 미결은 없다. 챕터 6~9 착수 시 볼 것은 §5 표 4건이다.

---

**newman 168/168 · DB H10/10 · 기능 ✅ 30 · ⚠ 4 · ❌ 0 · — 0**
