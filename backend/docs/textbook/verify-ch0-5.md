# 중간 점검 B — 챕터 0~5 전체: 기능 · DB 연결 · API 69개 Postman [예제 19]

> 작성 2026-09-06 · 전제: 챕터 5 보강(A-1 회향 하한·A-2 @Validated·B-1·B-2·B-3·B-5) 완료 후 실행
> 위치 `backend/docs/textbook/verify-ch0-5.md` · 산출물 `backend/docs/audit/feature-status-ch0-5.md` + 컬렉션 폴더 D·R 추가
> 챕터 2-14(규약 9종)·중간 점검 A(챕터 0~3 기능 30개)의 확장판. 이번 기준은 셋 — **① 기능이 되는가 ② DB가 계속 붙어 있는가 ③ API 69개가 전부 응답하는가**

---

## 1. 기능 목록 — Claude Code가 결과 열을 채운다 (✅ / ⚠ 일부 / ❌ / — 범위 밖)

| # | 기능 | 확인 요청(폴더) | 통과 기준 | 결과 |
|---|---|---|---|---|
| **인증·계정 (챕터 1)** | | | | |
| 1 | 가입 · 비밀번호 규칙 · 중복 이메일 | A01~A03 | 201 / 400 fields[password] / 409 AUTH-4090 | |
| 2 | 로그인 · 없는 계정과 같은 문구 | A04~A06 | 200 expiresIn 1800 · 401 AUTH-4011 문구 동일 | |
| 3 | 5회 실패 즉시 403 잠금 · 맞는 비밀번호도 403 | A07x·A08 | 5회째 403 AUTH-4031 | |
| 4 | refresh 회전 · 재사용 탐지 → 전 기기 폐기 · /reissue 404 | A09~A13 | AUTH-4015 후 최신 쿠키도 401, SQL alive=0 | |
| 5 | 로그아웃(전 기기·비로그인 멱등) | A14·A15 | Max-Age=0 · 후속 refresh 401 | |
| **회원 (챕터 2)** | | | | |
| 6 | 내 정보 · 내부 필드 비노출 · 잘못된 tier | U01~U04 | password/lockedUntil 없음 · 400 | |
| 7 | 약관 POST 배열 · 재동의 멱등 · DELETE 경로변수 | U05~U07 + U08(신규) | 1행 유지 · DELETE 200 후 0행 | |
| **마스터 (챕터 2·3)** | | | | |
| 8 | 권역 10 · 코스 있는 권역 9 · code | M01 | items 10, courseCount>0 인 것 9 | |
| 9 | 코스 목록/상세 · candidates · RIDER 정렬 · congested | M02~M05 + M18(신규) | sites 5 · candidates ≥1 · RIDER 면 SUNROAD 먼저 · 부산·동부 congested=true | |
| 10 | 사찰 기본/페이지 8블록/가는 법 7칸 · 언어 4단 · tier 덮기 | M06~M17 | 조계사 1~5 present=false | |
| 11 | 내려간 코스·사찰 404 | C06·S-status | 404 COURSE-4041 / SITE-4040 | |
| 12 | 관리자 사찰·코스·요소·뷰포인트·뱃지·이동시간 · status 본문 400 | S·C 폴더 | 기존 통과 유지 | |
| 13 | 카카오 검색 | K01~K03 | 200 places[0] (키 있음) | |
| **순례·여권 (챕터 4)** | | | | |
| 14 | 시작 멱등 · 비활성 409 · 여권 3단 · 진행률 출처 통일 | P01~P10 | created true→false 같은 id | |
| **시드·후보 (챕터 4 마감)** | | | | |
| 15 | 사찰 110 · 코스 12 · 자리 60 · 후보 213 · 요소 342 · 멱등 | D01(SQL) | 두 번 실행 동일 | |
| 16 | 동명이찰 분리 · 좌표 0 목록 · 종단 미확인 8곳 DRAFT | D02~D04(SQL) | seed_key 유일 · 18곳 목록 · 8곳 DRAFT | |
| **현장 인증 (챕터 5)** | | | | |
| 17 | GPS: 후보 검증·LOW·반경 밖·위치동의 403·이미 발행 409+메시지 | T02~T04b·T11 | 각 코드 | |
| 18 | QR: 위조·다른 사찰·구버전 400 · 정상 QR_DONE | T06~T08·T14 | | |
| 19 | 미션: 문장 길이 · COMPLETED · 보상 · 진행률 · seen 기록 | T09·T10 + SQL | phrase_seen 1·task_seen 1·thinkbox MISSION 1 | |
| 20 | 이동시간 PENDING · 관리자 승인 · 여권 반영 | T12·T15 | completedCount 2 | |
| 21 | 60분 만료 → 409 + DB EXPIRED(잠금 앞) · 청소기 | JUnit | Lock wait 없음 | |
| 22 | 하루 5개(살아 있는 도장) · 예외 접수 2건 · 어제 행 재사용 시 오늘 한도 | T09b·T09c·T16 + JUnit(B-3) | 429 STAMP-4291/4292 | |
| 23 | by-slot 조회 · 남의 스탬프 403 | T11b·T13 | | |
| 24 | **회향 하한** — ACTIVE 코스 1개로 5칸 완주 시 코스 인증서·쿠폰만, 회향 없음 | T10 후 SQL D05 | certificate HOEHYANG 0 · 회향 보상 0 · ebook HOEHYANG 0 | |
| **이후 챕터 코드 스모크 (챕터 6~9 — 종단 테스트 없음, 200/빈 목록만 확인)** | | | | |
| 25 | 생각상자 CRUD · is_edited | R01~R04 | 본문 수정 시 isEdited true | |
| 26 | 명상 목록/상세(비로그인) · 재생 기록 | R05~R07 | | |
| 27 | 보상 목록 · 수령 신청(PHYSICAL 없으면 400/404) | R08·R09 | | |
| 28 | 인증서 목록 · 공개 진위 확인(마스킹 닉네임) | R10·R11 | 닉네임 "순*자" 형태 | |
| 29 | 전자책 목록 · 다운로드 URL · 인쇄 신청/조회/취소 | R12~R15 | 회향본 없으면 인쇄 신청 4xx | |
| 30 | 업로드 presign(purpose 검증) | R16·R17 | 200 fileKey PHOTO/{userId}/… · 잘못된 purpose 400 | |
| 31 | 콘텐츠 예절 안내 · i18n | R18·R19 | | |
| 32 | 관리자 보상 심사 · 전자책 파일 등록 · 인쇄 주문 상태 · 원고 등록 | R20~R24 | 200 (권한 USER 는 403) | |
| **규약·보안·배포** | | | | |
| 33 | 좌표 필드 차단 · ADMIN 분리 · 없는 보호 URL 401 · /env 401 · /health UP | G01~G04 + G05(신규) | | |
| 34 | X-Request-Id 32자 · 클라이언트 값 그대로 · timestamp +09:00 | 공통 Tests + G06(신규) | | |

— 범위 밖: F-01 인트로(서버 없음) · 실제 S3 · 실제 전자책 조판(외부 배치).

---

## 2. DB 연결 지속성 점검 (폴더 H — 컬렉션이 아니라 스크립트 `docs/verify/db-check.sh`)

| # | 검사 | 방법 | 통과 기준 |
|---|---|---|---|
| H1 | 기동 직후 연결 | `SELECT 1` via `/health` | UP, `components.db` 없음(노출 안 함)이 정상 |
| H2 | 커넥션 풀 누수 | newman 전체 1회 전후 `SHOW STATUS LIKE 'Threads_connected'` | 전후 차이 ≤ 풀 idle 수(HikariCP 기본 10) |
| H3 | 장시간 유휴 후 재연결 | newman 1회 → 10분 대기 → `/api/regions` | 200 (HikariCP `maxLifetime` < MySQL `wait_timeout` 확인, 아니면 정리.md §5-2 10번 추가) |
| H4 | 트랜잭션 잔류 | `SELECT COUNT(*) FROM information_schema.INNODB_TRX` | 0 (newman 끝난 뒤) |
| H5 | 잠금 대기 | `SHOW ENGINE INNODB STATUS` 의 LATEST DETECTED DEADLOCK | 없음 |
| H6 | 시간대 | `SELECT @@global.time_zone, @@session.time_zone, NOW()` | Asia/Seoul 또는 +09:00 (SYSTEM 이면 OS 확인·§5-2 9번) |
| H7 | 외래키 무결성 | `stamp.site_id`·`slot_site.site_id`·`course_site.site_id` 가 site 에 전부 존재 / `stamp.completed_course_site_id` 고아 0 | 0행 |
| H8 | 문자셋 | `SELECT DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='temple_stamp_project'` + 한글 닉네임 왕복 | utf8mb4_unicode_ci · "확인포인트" 그대로 |
| H9 | `sql.init` 멱등 | bootRun 2회 연속 → 표 31·시드 행 수 동일 | 동일 |
| H10 | 트랜잭션 격리·잠금 순서 | JUnit 동시성 2건(순례 시작·제출 vs 승인) | 통과 |

---

## 3. API 69개 — Postman 폴더 배치

기존 폴더 A·U·P·M·G·K·S·C·T(132요청)에 **D·R·신규 6요청**을 더한다. 아래는 "어느 API가 어느 폴더에 있는가" 대조표다. 빈 칸이 없어야 한다.

| # | API | 폴더 |
|---|---|---|
| 1~4 | POST /api/auth/signup · login · refresh · logout | A |
| 5 | GET /api/regions | M01 |
| 6~7 | GET /api/courses · /{courseId} | M02~M05·M18 |
| 8~10 | GET /api/sites/{id} · /page · /guide | M06~M17 |
| 11~12 | GET /api/verses · /{verseNo} | M14~M16 |
| 13~14 | GET /api/meditations · /{id} | R05·R06 |
| 15~16 | GET /api/content/guide · /i18n/{lang} | R18·R19 |
| 17 | GET /api/certificates/verify/{serialNo} | R11 |
| 18 | GET /health | G03 |
| 19~23 | /api/users/me · agreements (GET·PATCH·GET·POST·DELETE) | U01~U08 |
| 24 | GET /api/passport | P |
| 25 | POST /api/pilgrimages | P |
| 26~31 | stamps: gps-check · qr · mission · evidence · GET /{id} · by-slot | T |
| 32~35 | thinkbox POST·GET·PATCH·DELETE | R01~R04 |
| 36 | POST /api/meditations/logs | R07 |
| 37~38 | GET /api/rewards · POST claim | R08·R09 |
| 39 | GET /api/certificates | R10 |
| 40~41 | GET /api/ebooks · /{id}/download-url | R12·R13 |
| 42~45 | print-orders POST·GET·GET /{id}·DELETE | R14·R15 |
| 46 | GET /api/uploads/presign | R16·R17 |
| 47~53 | admin/sites GET·POST·PUT·PATCH status·viewpoints·badges·elements(POST·DELETE) | S |
| 54~55 | admin/sites/{id}/qr · rotate | T01·T14 |
| 56~58 | admin/courses POST·PUT·PATCH status | C |
| 59 | PUT /api/admin/site-distances | C07 |
| 60~61 | admin/stamps/pending · review | T15 |
| 62~63 | admin/rewards/claims/pending · review | R20·R21 |
| 64 | GET /api/admin/kakao/places | K |
| 65~66 | admin/contents/phrases · missions | R22·R23 |
| 67~69 | admin/ebooks/{id}/files · print-orders · status | R24·R25·R26 |

**신규 요청 정의(요청 이름 → 기대)**

| 폴더 | 요청 | 기대 |
|---|---|---|
| U | U08 DELETE /api/users/me/agreements/LOCATION_SERVICE → GET 0건 → 다시 POST(T 폴더 전제) | 200 / 0 / 200 |
| M | M18 GET /api/courses/{데모}?target=RIDER | candidates 첫 항목 track SUNROAD(있을 때) |
| D | D01~D05 는 SQL(`docs/verify/seed-v4-check.sql` 재사용 + D05 회향 0) | |
| R | R01 POST thinkbox → R02 GET → R03 PATCH content → isEdited true → R04 DELETE | 201/200/200/200 |
| R | R05 GET meditations(비로그인) · R06 /{id} · R07 POST logs | 200 |
| R | R08 GET rewards(T10 후 ≥1) · R09 POST claim (STAMP 타입 → 400 REWARD-4001) | |
| R | R10 GET certificates(T15 후 코스 인증서 1) · R11 GET verify/{serial} 비로그인 → holderNickname 마스킹 | |
| R | R12 GET ebooks(PILGRIMAGE QUEUED 1) · R13 download-url(파일 없음 → 4xx EBOOK-4092) · R14 POST print-orders(회향본 아님 → 4xx) · R15 GET print-orders 0건 | |
| R | R16 presign purpose=PHOTO → fileKey `PHOTO/{userId}/` · R17 purpose=X → 400 UPLOAD-4001 | |
| R | R18 content/guide · R19 i18n/ko · i18n/xx → 404 또는 ko 폴백(실측 기록) | |
| R | R20 admin rewards pending · R21 review(없으면 404) · R22 phrases 등록(UPSERT) · R23 missions · R24 ebooks files(pdf 없으면 400) · R25 print-orders 목록 · R26 status(없으면 404) — USER 토큰으로는 전부 403 1건 추가 | |
| G | G05 GET /api/no-such → 401 AUTH-4013 · G06 X-Request-Id: abc123 보내면 응답 헤더 동일 | |

---

## 4. Claude Code 지시문

```
루트 src/ 작업. git 없음. 원문 backend/docs/textbook/verify-ch0-5.md. 전제: docs/audit/ch5-hardening.md 가 ⚠ 0 (아니면 그것부터). 목적은 점검·보고 — 코드 수정은 컬렉션·스크립트와 "명백한 결함" 뿐이고, 결함은 고치지 말고 표에 적는다(단, 5xx 는 원인까지).

STEP 1 — 컬렉션 확장: 폴더 U(U08)·M(M18)·G(G05·G06) 요청 추가, 폴더 R(R01~R26) 신설(T 다음), 폴더 D 는 SQL 이므로 seed-v4-check.sql 에 D05(회향 0) 추가. cleanup.sql 에 thinkbox DIRECT·meditation_log·print_order 정리 추가. 본문은 docs/verify/body/*.json (한글 규칙).

STEP 2 — DB 점검 스크립트 docs/verify/db-check.sh: §2 H1~H9 를 순서대로 실행해 결과를 docs/verify/db-check-result.txt 에. H3 의 10분 대기는 실제로 기다린다(HikariCP maxLifetime·MySQL wait_timeout 값도 함께 기록). H10 은 JUnit 결과 인용.

STEP 3 — 실행: ./gradlew test → bootRun → db-check.sh(전반) → run-all.sh 2회 → db-check.sh(후반, H2·H4·H5) → 정리. newman 요청·단언 수, 실패 전부 원문.

STEP 4 — 보고서 docs/audit/feature-status-ch0-5.md: §1 표 34행 결과 채움(근거 요청 이름·코드) / §2 H1~H10 표 / §3 API 69개 ↔ 폴더 대조에서 누락 0 확인 / 이후 챕터(R 폴더)에서 발견된 결함은 "챕터 6~9 착수 시 수정" 표로 분리 / [질문]. 마지막 줄 "newman N/N · DB H10/10 · 기능 ✅ a · ⚠ b · ❌ c · — d".

STEP 5 — 정리.md §1 갱신(테스트·요청·단언 수), §6 에 새 함정이 있으면 추가. bootRun 종료. 챕터 6 은 시작하지 않는다.
```

**예성 직접**: 이 파일 → `backend/docs/textbook/`, 지시문 붙여넣기, 보고서 마지막 줄 확인. R 폴더에서 나온 결함 표는 챕터 6~9 지시문의 첫 STEP이 됩니다.
