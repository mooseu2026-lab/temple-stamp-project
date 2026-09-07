# 전체 점검 C — 챕터 0~6 기능·런타임·보안·DB 현황

> 2026-09-06 · 원문 `backend/docs/textbook/verify-ch0-6.md` · 전제 `docs/audit/ch6-record.md` ⚠ 0 + Q-a 반영
> 목적은 점검·보고다. 이번에 손댄 코드는 **컬렉션·스크립트뿐**이고, 제품 코드 결함은 고치지 않고 표에 적었다.

## 한눈에

```
JUnit      151 / 151            (동시성 2건 포함 — H10)
newman     요청 265 / 265 · 단언 1182 / 1182   (2회 연속 같은 수)
X 폴더 단독 요청  66 / 66 · 단언 286 / 286
R5 동시성  ta 158/158 · tb 158/158 · 교착 0 · 5xx 0   (계정 conc-a / conc-b)
런타임     ERROR 0 · 스택트레이스 0 · 5xx 0 · WARN 598 중 허용목록 밖 0
보안       스크립트 16항 전부 통과(S5·S6·S7·S11·S12) + X 폴더 38건(S1~S4·S8~S10)
DB         H1~H12 전부 통과 (H3 은 10분을 실제로 기다렸다)
엔드포인트  73개 · 컬렉션이 부르는 것 71 · 미검증 2 (둘 다 챕터 9)
```

---

## 1. 기능

### 1-1. 실현된 기능 (챕터 0~6) — 35행

| # | 기능 | 폴더 | 근거 | 결과 |
|---|---|---|---|---|
| 1 | 가입(중복 409 · 비밀번호 규칙 400) | A | A01~A03 | ✅ |
| 2 | 로그인·토큰 발급 | A | A04~A06 · 쿠키 HttpOnly/SameSite(S5) | ✅ |
| 3 | 5회 실패 잠금 | A | A07·A08 AUTH-4291 | ✅ |
| 4 | refresh 회전·재사용 탐지 | A | A09·A10·A12 — 재사용 시 전 기기 폐기 | ✅ |
| 5 | 로그아웃·폐기 | A | A13·A14 | ✅ |
| 6 | 회원 정보 조회·수정 | U | U01~U04 (닉네임 중복 409) | ✅ |
| 7 | 약관 동의 등록·조회·철회 | U | U05~U08c — 위치동의 철회 후 GPS 403(S12) | ✅ |
| 8 | 권역 목록 | M | M01~M03 · region 10행 | ✅ |
| 9 | 코스 목록·상세 | M | M04~M07 · C05 공개 조회 | ✅ |
| 10 | 자리별 후보(candidates) | M | M11~M13 — 혼잡은 걸러내지 않고 `congested` 로 표시(ch4-close ②안) | ✅ |
| 11 | 사찰 페이지 | M | M08~M10 (참배 요소·뷰포인트·뱃지) | ✅ |
| 12 | 「가는 법」 | M | M08 응답 · ②안(코스 순서 기준) | ✅ |
| 13 | 관리자 마스터(사찰·코스·거리·요소·QR) · 카카오 | S·C·K | S01~S12 · C01~C09 · K01~K04 | ✅ |
| 14 | 순례 시작·여권·진행률 | P | P01~P08 · 동시 시작 1건(JUnit) | ✅ |
| 15 | 시드 110 / 코스 12 / 자리 218 / 요소 342 · 멱등 | D(SQL) | H9 — `slot_site` 218 = 시드 213 + 데모 5 | ✅ |
| 16 | 동명이찰 분리 · 좌표 · DRAFT | D(SQL) | `seed_key` 유일(H12) · 좌표 0 인 곳 1(봉인사, 콘텐츠 과제) | ✅ |
| 17 | GPS 도착 확인(반경·동의) | T | T02~T04b · STAMP-4030 | ✅ |
| 18 | 미션 인증(사진·문장) | T | T05·T09·T10(png 회귀 포함) | ✅ |
| 19 | QR 인증(서명·회전) | T | T06~T08 · T14a·T14b | ✅ |
| 20 | 세션 만료(15분)·만료 기록 | T | T-08 — 만료가 EXPIRED 로 남는다 | ✅ |
| 21 | 하루 한도 | T | T11·T12 STAMP-4291 — 살아 있는 도장만 센다 | ✅ |
| 22 | by-slot 조회 | T | T11b · 남의 것은 404(X47) | ✅ |
| 23 | 증빙 접수·관리자 심사 | T | T16a·T16b·T16 · T15·T15a · `site_id` 기록(JUnit T-16c) | ✅ |
| 24 | 완주·회향 하한 | T | 회향은 ACTIVE 코스 12개 이상일 때만 — A-1 수정 뒤 오발급 재현 없음 | ✅ |
| 25 | presign(jpeg·png·purpose·소유 검증) | V | V01~V03 · R16·R17 · 경로 조작 400(X48~X50) | ✅ |
| 26 | 사찰당 사진 1 + 문장 1(UPSERT·한 트랜잭션·is_edited) | V | V04~V08b · 유니크 실측(H12) | ✅ |
| 27 | 생각상자 CRUD·정렬·403·flashback | V | V09~V15 — 없는 해는 200 + `data:null` | ✅ |
| 28 | 명상 목록·상세(ACTIVE·lang 폴백)·기록 | V | V16~V19 · R05~R07 · `medlog` 고아 0(H11) | ✅ |
| 29 | 관리자 사찰 목록 `status` · `?status=` 필터 | S | S09 · V06b · G02 (ch6 Q-a 반영) | ✅ |
| 30 | 보상 적립·목록 (이후 챕터 코드 스모크) | R | R08·R09 200 — **claim 결함은 §1-2 5-1** | ✅(기준) |
| 31 | 인증서 목록·진위 확인 | R | R10·R11 — 닉네임 마스킹(S12) | ✅(기준) |
| 32 | 전자책·인쇄 신청 | R | R12~R15 · R24~R26 — 빈 목록/4xx 가 기대값 | ✅(기준) |
| 33 | 콘텐츠 원고·관리자 심사 | R | R18~R23 — **variantNo 결함은 §1-2 5-4** | ✅(기준) |
| 34 | 규약(응답 봉투·X-Request-Id·timestamp·좌표 차단) | G | G01~G06 · X61·X62 | ✅ |
| 35 | 공통 보안(401·403·`/env`·스택트레이스 없음) | G·X | X27~X42 · X51~X54 | ✅ |

**✅ 35 · ⚠ 0 · ❌ 0 · — 0**

"✅(기준)" 은 교재가 정한 통과 기준("200 또는 빈 목록")을 만족했다는 뜻이다. 그 안에서 보이는 동작 결함은 고치지 않고 §1-2 에 남겼다 — 챕터 7~9 의 일이다.

### 1-2. 남은 기능 (챕터 7~9 + 배포 + 콘텐츠)

| 챕터 | 기능 | 지금 상태 | 알려진 결함 |
|---|---|---|---|
| **7** | 코스 완주 재집계 · 인증서 발급/회수 · 보상 적립/수령 신청 · 회향 | 코드 있음, 종단 테스트 없음. 회향 하한만 검증됨 | **5-1** `claim` 이 `claimable:false` 도 200(R09 실측) → PHYSICAL·GRANTED·본인 검사 필요. 재신청 409 는 이미 동작(R28) |
| **8** | 확장문구 140편 · 미션 140편 · 심사 상태 | 관리자 API 있음, 원고 35/175편 | **5-4** 미션 `variantNo` 상한 없음(R30). `versionNo` 는 1~5 로 막혀 있다(R22b) |
| **9** | 전자책 대기열·조판·다운로드 · 인쇄 신청/주문 상태 · 옛 사진 파일 정리 | 코드 있음, 빈 상태로만 확인 | **5-2** `GET/DELETE /api/print-orders/{id}` 미실행(R29 · §2) · **5-5** 상태에 `PRINTING` 없음(R31) · **storage_orphan** 교체된 옛 파일 삭제 큐(ch6 Q-b) |
| 배포 | prod 프로파일 첫 기동 · 시크릿 · 스토리지 · ALTER 3건 · `time_zone` | 한 번도 안 띄움 | 정리.md §5 |
| 콘텐츠 | QR 위치 힌트 110곳 · 봉인사 좌표 · 원고 140편 | 답사·협회 | 봉인사는 카카오 장소 DB 에 없다(도로명 주소로도 0건) — 답사로만 채울 수 있다 |

---

## 2. API 대조 — **73개** (74 가 아니다)

`node backend/docs/verify/endpoint-scan.js` 가 컨트롤러 26개의 `@RequestMapping` 을 전수 스캔하고, `SecurityConfig` 매처로 접근 권한을 판정해 컬렉션 요청과 1:1 대조한다. 전체 목록은 `docs/audit/endpoints.md`.

| | |
|---|---|
| 엔드포인트 | **73** |
| 컬렉션이 부르는 것 | 71 |
| 미검증 | 2 |

**왜 74 가 아닌가** — 교재 §2-1 이 신규 74번으로 적은 `GET /api/admin/sites?status=` 는 새 엔드포인트가 아니라 13번 `GET /api/admin/sites` 의 **질의 파라미터**다. 경로가 늘지 않았으므로 69 + 4 = 73 이 맞다. 챕터 6 신규는 넷이다 — `PUT /api/photos/{siteId}` · `GET /api/photos/{siteId}` · `GET /api/photos` · `GET /api/thinkbox/flashback`.

**이번에 컬렉션을 채운 것 3건** (STEP 1 스캔이 찾아낸 구멍):

| 경로 | 추가한 요청 |
|---|---|
| `PUT /api/admin/courses/{courseId}` | C09 코스 수정 PUT — 같은 구성으로 다시 보내도 200 |
| `DELETE /api/admin/sites/{siteId}/elements/{elementCode}` | S12 참배 요소 삭제 — 그 자리가 다시 "없음" 이 된다 |
| `GET /api/photos/{siteId}` | V08b 사찰별 내 사진 조회 |

**남은 미검증 2건** — 둘 다 인쇄 주문이 실제로 있어야 부를 수 있다. 챕터 9 에서 검증한다.

| 메서드 | 경로 | 접근 |
|---|---|---|
| GET | `/api/print-orders/{printOrderId}` | 로그인 |
| DELETE | `/api/print-orders/{printOrderId}` | 로그인 |

---

## 3. 런타임 오류 스캔 — `runtime-check.sh`

| # | 검사 | 방법 | 결과 |
|---|---|---|---|
| R1 | 기동 로그 | `Started TempleStampApplication` 까지 35줄에서 WARN·ERROR·Exception | ✅ 0건 |
| R2 | 전 요청 뒤 로그 | newman 전체 실행 뒤 ERROR·스택트레이스·5xx·미처리 예외 | ✅ 전부 0건 · WARN 598 중 허용목록 밖 **0** |
| R3 | 잘못된 입력 퍼징 | X01~X21 (빈 본문 8 · 타입 오류 5 · 경로변수 6 · 10KB 2) | ✅ 전부 4xx · 5xx 0 |
| R4 | 인증 헤더 변형 | X22~X26 (빈 Bearer · 깨진 서명 · JWT 아님 · 다른 시크릿) | ✅ 전부 401 · 5xx 0 |
| R5 | 동시성 | JUnit 2건 + T 폴더 2 프로세스 동시(계정 conc-a·conc-b) | ✅ 158/158 · 158/158 · 교착 0 · 5xx 0 |
| R6 | 매퍼 정합 | 매퍼↔XML · `#{}`↔`@Param` · resultType 별칭 (select 75개) | ✅ 0 · 0 · 0 |
| R7 | 미사용 코드 | 목록만 보고 | 아래 |

**5xx 원문**: 없다. 이번 점검에서 5xx 는 한 건도 나오지 않았다. 챕터 5 에서 잡았던 `Lock wait timeout exceeded` 는 [기본 46·47] 수정 뒤 재현되지 않는다(정리.md §6-7).

**허용 목록** — `runtime-check.sh` 상단에 "왜 문제가 아닌가" 를 한 줄씩 함께 적어 둔다. 이유 없는 허용은 경고를 지우는 것과 같다.

| 패턴 | 왜 문제가 아닌가 |
|---|---|
| `Sharing is only supported` | JVM 이 CDS 아카이브를 못 쓸 때의 안내 |
| `GlobalExceptionHandler` | 검증 세트가 **일부러** 4xx 를 내며 남기는 WARN |
| `SEED ` · `no-coord` | 시더가 남기는 안내 |
| `deprecat` · `--enable-native-access` · `An illegal reflective` | 빌드·JVM 경고 |
| `좌표 필드 반입 차단` | CoordinateFieldGuard 가 막은 것 — X61·X62 가 일부러 보낸다 |
| `No mapping for` | 없앤 경로를 일부러 부른 것(A11 · P09 · P10 · G05) |
| `폐기된 리프레시 토큰 재사용` | 재사용 탐지가 동작한 것(A12) — 안 찍히면 오히려 문제다 |
| `세션 만료로 닫음` | 만료 기록이 동작한 것(T-08) |

**R7 — 참조 0건 (보고만, 고치지 않았다)**

`ErrorCode` 77개 중 11개가 코드에서 한 번도 쓰이지 않는다:
`COMMON_4290` `COMMON_5030` `AUTH_4001` `COURSE_4042` `UPLOAD_4000` `UPLOAD_4130` `REWARD_4030` `REWARD_4090` `CERT_4090` `ADMIN_4030` `ADMIN_4040`

`STAMP_4003` 은 예약이라 목록에서 뺐다. `COURSE_4042` 는 정리.md §7-3 에서 `COMMON-4000` 으로 대체하기로 한 코드다. `REWARD_*`·`CERT_*` 는 챕터 7, `UPLOAD_4130` 은 챕터 9 에서 쓰인다.

미참조 매퍼 메서드 7개:
`CertificateMapper.updateFileKey` · `MeditationLogMapper.sumPlayedSec` · `PhotoMapper.updatePrivacy` · `RewardPolicyMapper.findByCode` · `SiteDistanceMapper.findFrom` · `UserMapper.updatePassword` · `ExpansionPhraseMapper.countReviewed`

---

## 4. 보안 점검 — `security-check.sh` + X 폴더

| # | 항목 | 근거 | 결과 |
|---|---|---|---|
| S1 | 인증 없는 접근 | X27~X34 — `/users/me` `/passport` `/thinkbox` `/photos` `/rewards` `/uploads/presign` `/stamps/by-slot` `/admin/sites` 전부 401 | ✅ |
| S2 | 권한 상승 | X35~X42 · R27 — USER 토큰으로 관리자 경로 8종 전부 403 | ✅ |
| S3 | 수평 권한 | X43~X47 — 남의 스탬프·생각상자·사진·by-slot 전부 4xx, `data` 는 null(본문 유출 0) | ✅ |
| S4 | 좌표 필드 차단 | X61·X62 — 사용자 경로에 `latitude` 를 실으면 400 `COMMON-4001`. 관리자 경로만 통과(S01~S04) | ✅ |
| S5 | 토큰 | `access-seconds: 1800` · `SELECT COUNT(*) FROM refresh_token WHERE token_hash LIKE '%.%' OR CHAR_LENGTH(token_hash) <> 64` → 0 · Set-Cookie 에 HttpOnly·SameSite · `application-prod.yml` `secure: true` · 재사용 탐지 A12 | ✅ 5/5 |
| S6 | 시크릿 | JWT 시크릿 ≠ QR 시크릿(값은 비교만, 기록하지 않는다) · `.gitignore` 에 `.env.local` · 로그 grep 0건 · 소스에 민감값 로그 0건 | ✅ 4/4 |
| S7 | 비밀번호 | BCrypt 접두(`$2a$`/`$2b$`/`$2y$`) 13 / 13 계정 · 해시 최소 길이 60 | ✅ 2/2 |
| S8 | 업로드 | X48 상위 경로(`../`) 400 · X49 용도 불일치(EVIDENCE 키를 PHOTO 자리에) 400 · X50 contentType 허용 밖 400 · 서버는 바이트를 받지 않는다(presign 만) | ✅ |
| S9 | 정보 노출 | X51~X53 `/env`·`/actuator/**` 401 · X54 오류 응답에 스택트레이스 없음(`X-Request-Id` 만) | ✅ |
| S10 | 입력 | X55~X58 인젝션 문자열이 값으로만 다뤄지고 표가 살아 있다 · X59 301자 400 · X60 깊은 JSON 400 | ✅ |
| S11 | CORS | 허용 오리진에 `*` 없음 · 허용 밖 오리진 preflight 응답에 `Access-Control-Allow-Origin` 헤더 0개 | ✅ 2/2 |
| S12 | 개인정보 | `StampService.requireLocationAgreement` · `CertificateVerifyResponse.maskNickname` · 전자책 수록 질의가 `is_private = 0` 만 고른다 | ✅ 3/3 |

스크립트 자동 판정 **통과 16 · 실패 0** (`backend/docs/verify/security-check-result.txt`). 키 값 자체는 보고서에도 로그에도 적지 않았다 — 같은지 다른지만 비교했다.

---

## 5. DB 연결·정합 — `db-check.sh`

| # | 검사 | 실측 | 결과 |
|---|---|---|---|
| H1 | 기동 직후 연결 | `/health` 200 `{"status":"UP"}` · DB 왕복 1 | ✅ |
| H2 | 커넥션 누수 | newman 전 13 → 후 13 (풀 크기 10 이내) | ✅ |
| H3 | 10분 유휴 후 재연결 | **실제로 10분 대기** 뒤 `/api/regions`·`/api/courses/1`·`/health` 전부 200. MySQL `wait_timeout` 28800초 > HikariCP `maxLifetime` 30분 — 순서가 맞다 | ✅ |
| H4 | 트랜잭션 잔류 | `INNODB_TRX` 0행 | ✅ |
| H5 | 잠금 대기·교착 | LATEST DETECTED DEADLOCK 없음 · 현재 잠금 대기 0 | ✅ |
| H6 | 시간대 | global/session `SYSTEM`, `NOW()` 가 호스트 KST 와 일치 | ✅ (prod 는 정리.md §5-2 9번) |
| H7 | 외래키 무결성 | `stamp.site_id` · `slot_site` · `course_site` · `completed_course_site_id` 고아 전부 0 | ✅ |
| H8 | 문자셋 | `utf8mb4` / `utf8mb4_unicode_ci` · 한글 왕복 그대로 | ✅ |
| H9 | `sql.init` 멱등 | 표 31 · 사찰 110 · 코스 12 · `slot_site` 218 · 요소 342 · region 10 — 재기동해도 동일 | ✅ |
| H10 | 트랜잭션 격리·잠금 순서 | JUnit 동시성 2건 — 순례 동시 시작이 1건으로 · 미션 제출 vs 관리자 승인 교착 없음 | ✅ |
| H11 | 챕터 6 표 정합 | `photo`·`thinkbox` 사찰 고아 0 · `meditation_log` 고아 0 · 사찰별 DIRECT 문장 중복 0행 | ✅ |
| H12 | 유니크 제약 **실측** | 중복 INSERT 를 실제로 넣어 셋 다 1062 로 막혔다 — `photo.uk_photo_user_site` · `site.uk_site_seed_key` · `stamp.uk_stamp_completed` | ✅ |

---

## 6. 이번에 손댄 것 (제품 코드 아님)

| 파일 | 무엇 | 왜 |
|---|---|---|
| `postman/temple-stamp-all.postman_collection.json` | X 폴더 66건 신설 · S12·C09·V08b 추가 · R28~R31 재현 4건 · X00~X00d 자체 로그인 | 미검증 엔드포인트를 메우고, X 폴더만 단독으로 돌려도 서게 |
| `backend/docs/verify/endpoint-scan.js` | 신설 | 손으로 센 API 개수를 믿지 않기 위해 |
| `backend/docs/verify/runtime-check.sh` | 신설 | R1·R2·R6·R7 |
| `backend/docs/verify/security-check.sh` | 신설 | S5·S6·S7·S11·S12 |
| `backend/docs/verify/db-check.sh` | H11·H12 추가 | 챕터 6 표와 유니크 제약 |

스캐너·스크립트에서 잡은 내 실수 셋. 도구를 믿기 전에 도구를 검증해야 한다는 뜻이다.

- `SecurityConfig` 의 `OPTIONS /**` 를 permitAll 로 세면 전 경로가 공개로 보인다 — preflight 전용이라 따로 담아야 한다.
- 미참조 판정에서 선언 줄까지 세면(`uses <= 1`) 89건이 뜬다. 호출부만 세야 7건이다.
- `grep 'access-seconds'` 는 바로 위 **주석 줄**을 먼저 잡는다. 들여쓴 설정 줄만 골라야 값이 나온다.

---

## [질문]

**없음.** 챕터 0~6 범위에서 ⚠·❌ 0.

답이 필요한 것은 아니지만 순서로 적어 둔다 — §1-2 의 **5-1**(`claim` 이 `claimable:false` 도 200)이 챕터 7 의 첫 작업이 되어야 한다. 지금은 R09 가 200 을 받는 것이 "현재 동작" 으로 컬렉션에 박혀 있고, 챕터 7 에서 이 기대값이 뒤집힌다. R28 의 409(재신청 거절)는 그대로 유지된다.

---

newman 265/265 · X 66/66 · DB H12/12 · 보안 S12/12 · 런타임 5xx 0 · 기능 ✅ 35 · ⚠ 0 · ❌ 0 · — 0
