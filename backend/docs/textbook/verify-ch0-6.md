# 전체 점검 C — 챕터 0~6: 기능 · 런타임 · 보안 · DB · API 완료/잔여 · Postman [예제 20]

> 작성 2026-09-06 · 전제: 챕터 6 [질문] 답(Q-a 관리자 목록 status) 반영 후 · 위치 `backend/docs/textbook/verify-ch0-6.md`
> 산출물 `backend/docs/audit/feature-status-ch0-6.md` · 스크립트 `backend/docs/verify/{db-check.sh, security-check.sh, runtime-check.sh}`
> 중간 점검 B(verify-ch0-5)의 확장. 이번에 **새로 더한 축**은 둘 — **런타임 오류 스캔**과 **보안 점검 12항**. 나머지는 B 를 다시 돌려 챕터 6 이후에도 깨진 것이 없는지 본다.

---

## 1. 기능 — 실현된 것 / 남은 것

### 1-1. 실현된 기능 (챕터 0~6) — Claude Code 가 결과 열을 채운다

| # | 기능 | 폴더 | 통과 기준 | 결과 |
|---|---|---|---|---|
| 1~5 | 인증·계정(가입·로그인·잠금·refresh 회전/재사용 탐지·로그아웃) | A | B 와 동일 | |
| 6~7 | 회원 정보·약관 | U | | |
| 8~13 | 권역·코스·candidates·사찰 페이지·가는 법·관리자 마스터·카카오 | M·S·C·K | | |
| 14 | 순례 시작·여권·진행률 | P | | |
| 15~16 | 시드 110/12/60/213/342 · 동명이찰 · 좌표(미확보 1곳 봉인사) | D(SQL) | | |
| 17~24 | 현장 인증 3단계 전부(후보·한도·만료·by-slot·회향 하한) | T | | |
| **25** | **presign(jpeg·png·purpose·소유 검증)** | V01~V03 | | |
| **26** | **사찰당 사진 1 + 문장 1(UPSERT·한 트랜잭션·is_edited)** | V04~V08 | | |
| **27** | **생각상자 CRUD·정렬·403·flashback 200/null** | V09~V15 | | |
| **28** | **명상 목록/상세(ACTIVE·lang 폴백)·기록** | V16~V19 | | |
| **29** | **관리자 사찰 목록 status·?status= 필터**(ch6 Q-a) | S 신규 | | |
| 30~33 | 이후 챕터 코드 스모크(보상·인증서·전자책·인쇄·콘텐츠·관리자 심사) | R | 200/빈 목록 | |
| 34~35 | 규약·보안 공통(좌표 차단·ADMIN·401·/env·X-Request-Id·timestamp) | G | | |

### 1-2. 남은 기능 (챕터 7~9 + 배포 + 콘텐츠)

| 챕터 | 기능 | 지금 상태 | 알려진 결함(중간 점검 B·챕터 6에서 발견) |
|---|---|---|---|
| **7** | 코스 완주 재집계 · 인증서 발급/회수 · 보상 적립/**수령 신청** · 회향 | 코드 있음, 종단 테스트 없음. 회향 하한만 검증됨 | 5-1 `claim`이 `claimable:false`도 200 → PHYSICAL+GRANTED+본인 검사 |
| **8** | 콘텐츠 원고 등록(확장문구 140편·미션 140편)·심사 상태·관리자 검증 | 관리자 API 있음, 원고 35/175편 | 5-4 미션 `variantNo` 상한 없음 |
| **9** | 전자책 대기열·조판·다운로드 · 인쇄 신청/주문 상태 · 옛 사진 파일 정리(ch6 Q-b) | 코드 있음, 빈 상태로만 확인 | 5-2 인쇄 GET/DELETE 미실행 · 5-5 `PRINTING` 상태 없음 |
| 배포 | prod 프로파일 첫 기동 · 시크릿 · 스토리지 · ALTER 3건 · time_zone | 한 번도 안 띄움 | 정리.md §5 |
| 콘텐츠 | QR 위치 힌트 110곳(답사) · 봉인사 좌표 · 원고 140편 | 예성/협회 | 모든 챕터 뒤 |

## 2. API — 실현된 것 / 남은 것

### 2-1. 실현·검증된 API (74개)

중간 점검 B의 69개 + 챕터 6 신규 5개:

| 신규 | 경로 | 폴더 |
|---|---|---|
| 70 | `PUT /api/photos/{siteId}` | V04~V07 |
| 71 | `GET /api/photos/{siteId}` | V |
| 72 | `GET /api/photos` | V08 |
| 73 | `GET /api/thinkbox/flashback` | V14·V15 |
| 74 | `GET /api/admin/sites?status=` (필터·응답 status) | S 신규 |

**대조 규칙**: 컨트롤러의 `@RequestMapping` 을 전수 스캔해 실제 엔드포인트 목록을 만들고, 컬렉션의 요청과 1:1 대조한다. **컬렉션에 없는 엔드포인트 = 미검증**으로 표에 남긴다(69→74 가 맞는지 스캔으로 확정).

### 2-2. 남은 API — 코드는 있으나 종단 검증이 없는 것 (챕터 7~9에서 검증)

| 경로 | 챕터 | 남은 검증 |
|---|---|---|
| `POST /api/rewards/{id}/claim` | 7 | claimable 서버 재확인·중복 신청 409 |
| `GET /api/certificates` · `/verify/{serial}` | 7 | 발급·회수·마스킹·위조 serial 404 |
| `GET/POST /api/admin/rewards/claims/**` | 7 | 심사 흐름 |
| `POST /api/admin/contents/phrases` · `missions` | 8 | UPSERT·범위·심사 상태 전이 |
| `GET /api/ebooks` · `/download-url` | 9 | 조판 완료 후 URL·미완성 4xx |
| `POST/GET/DELETE /api/print-orders` · admin print-orders | 9 | 상태 전이(PRINTING 추가)·취소 조건 |
| `PUT /api/admin/ebooks/{id}/files` | 9 | 파일 등록 후 READY |

## 3. 런타임 오류 스캔 (신규) — `runtime-check.sh`

| # | 검사 | 방법 | 통과 |
|---|---|---|---|
| R1 | 기동 로그 | `bootRun` 로그에서 `WARN`·`ERROR`·`Exception` grep | 0건(허용 목록: HikariCP 풀 안내 등 명시) |
| R2 | 74개 엔드포인트 **전부** 1회 호출 후 로그 | newman 전체 실행 뒤 로그 `ERROR`·스택트레이스 | 0건 |
| R3 | 잘못된 입력 퍼징 | 각 POST/PUT/PATCH 에 빈 본문 `{}` · 잘못된 타입(`"abc"` in 숫자) · 경로변수 `0`·`-1`·`abc` · 10KB 문자열 | **전부 4xx**, 5xx 0 |
| R4 | 인증 헤더 변형 | `Bearer ` 빈 값 · 만료 토큰 · 서명 깨진 토큰 · 다른 시크릿으로 만든 토큰 | 401 AUTH-4012/4013, 5xx 0 |
| R5 | 동시성 | JUnit 동시성 2건 + newman T 폴더를 2 프로세스 동시 실행(계정 다르게) | 교착·500 0 |
| R6 | 매퍼 정합 | 매퍼↔XML·`#{}`↔`@Param`·별칭 미채움 전수(5 보강에서 한 방법) | 0 |
| R7 | 미사용 코드 | ErrorCode 상수 중 참조 0건 목록(STAMP-4003 예약은 허용) · 미참조 매퍼 메서드 | 목록만 보고 |

## 4. 보안 점검 (신규) — `security-check.sh` + 정적 확인

| # | 항목 | 검사 | 통과 |
|---|---|---|---|
| S1 | 인증 없는 접근 | 보호 경로 74개 중 permitAll 목록 밖 전부 → 401 | 목록과 일치 |
| S2 | 권한 상승 | USER 토큰으로 `/api/admin/**` 전부 → 403 | 22개 전부 403 |
| S3 | 수평 권한(남의 자원) | 다른 계정으로 stamp·thinkbox·photo·print-order·reward 접근 | 403/404, 본문 유출 0 |
| S4 | 좌표 필드 차단 | 사용자 경로에 `latitude` 실으면 400 · 관리자 경로만 통과 | CoordinateFieldGuard |
| S5 | 토큰 | refresh 재사용 → 전 기기 폐기 · 액세스 30분 · 쿠키 HttpOnly·SameSite · prod `Secure` | |
| S6 | 시크릿 | JWT≠QR · `.env.local` 미커밋 · 로그에 토큰·비밀번호·시크릿 미출력(grep) | |
| S7 | 비밀번호 | BCrypt 저장 확인(SQL 접두 `$2a$`) · 규칙 400 · 동일 문구 401 | |
| S8 | 업로드 | presign 키 소유 검증 · `..`·`/` 경로 조작 400 · contentType 화이트리스트 · 서버가 바이트 미수신 | |
| S9 | 정보 노출 | `/env`·`/actuator/**` 401 · 500 응답에 스택트레이스 없음 · `X-Request-Id` 만 | |
| S10 | 입력 | 300자 상한 · 10KB 본문 413/400 · SQL 인젝션 문자열(`' OR 1=1`)이 400/정상 처리 · JSON 깊이 폭탄 400 | |
| S11 | CORS | 허용 오리진 외 preflight 거부 · `allow-credentials` 와 `*` 동시 금지 | |
| S12 | 개인정보 | 위치동의 없이 GPS 403 · 인증서 공개 조회 닉네임 마스킹 · `is_private`/`has_other_face` 전자책 제외(SQL) | |

## 5. DB 연결·정합 — `db-check.sh` (B 의 H1~H10 재실행 + 2개 추가)

| # | 추가 검사 | 통과 |
|---|---|---|
| H11 | 챕터 6 표 정합: `photo`↔`thinkbox DIRECT` 고아 0 · `meditation_log.meditation_id` 전부 존재 | 0행 |
| H12 | 유니크 제약 실측: `photo(user,site)`·`thinkbox(stamp)`·`site(seed_key)` 중복 INSERT → 오류 | 제약 동작 |

## 6. Postman — 이번에 확인할 것

| 폴더 | 요청 수 | 이번 점검의 초점 |
|---|---|---|
| A·U·P·M·G·K·S·C | 기존 | 회귀 0 · S 에 `?status=` 신규 1 |
| T | 31 | png 사진 회귀(T10) |
| V | 24 | 챕터 6 전부 |
| R | 30 | 이후 챕터 코드가 **여전히 200/빈 목록**인지 · 5-1·5-2·5-4·5-5 재현 요청 4개 추가(기대값은 "현재 동작" 그대로, 챕터 7~9 에서 뒤집힘) |
| **X 런타임·보안(신규)** | ~40 | R3·R4·S1~S3·S8~S10 을 요청으로: 빈 본문·타입 오류·경로변수 이상·헤더 변형·남의 자원·경로 조작·인젝션 문자열 |

## 7. Claude Code 지시문

```
루트 src/ 작업. git 없음. 원문 backend/docs/textbook/verify-ch0-6.md. 전제 docs/audit/ch6-record.md ⚠ 0 + Q-a 반영. 목적은 점검·보고 — 코드 수정은 컬렉션·스크립트와 5xx 원인뿐, 결함은 표에 적는다. 챕터 7 은 시작하지 않는다.

STEP 1 — 엔드포인트 전수 스캔: 컨트롤러 @RequestMapping 을 스캔해 실제 목록(메서드·경로·permitAll 여부·ROLE) 을 docs/audit/endpoints.md 로. 컬렉션 요청과 1:1 대조 → 미검증 엔드포인트 표. 74개인지 확정.

STEP 2 — 컬렉션: S 에 ?status=DRAFT 1건, R 에 5-1·5-2·5-4·5-5 재현 4건(기대 = 현재 동작, 주석에 "챕터 N 에서 뒤집힘"), 폴더 X(런타임·보안 ~40건: §3 R3·R4, §4 S1·S2·S3·S8·S9·S10) 신설. 본문은 docs/verify/body/. cleanup.sql 갱신.

STEP 3 — 스크립트: runtime-check.sh(§3 R1·R2·R7 — 로그 grep 허용 목록 포함, R6 매퍼 정합) · security-check.sh(§4 S5·S6·S7·S11·S12 — 정적 grep + SQL) · db-check.sh 에 H11·H12 추가.

STEP 4 — 실행: ./gradlew test → bootRun(로그 파일로) → db-check.sh 전반 → run-all.sh 2회 → X 폴더 별도 1회 → runtime-check.sh → security-check.sh → db-check.sh 후반 → 정리. R5 동시성: T 폴더 2 프로세스 동시(계정 2개).

STEP 5 — 보고서 docs/audit/feature-status-ch0-6.md: §1-1 기능 35행 결과 / §1-2 남은 기능 표(그대로) / §2 API 74 대조·미검증 목록 / §3 런타임 R1~R7 표(허용 목록·5xx 원문) / §4 보안 S1~S12 표(근거 요청·SQL) / §5 DB H1~H12 / [질문]. 마지막 줄 "newman N/N · X N/N · DB H12/12 · 보안 S12/12 · 런타임 5xx 0 · 기능 ✅ a · ⚠ b · ❌ c · — d".

STEP 6 — 정리.md §1 갱신, §6 새 함정, §5 보안 결과 반영. bootRun 종료.
```

**예성 직접**: 이 파일 → `backend/docs/textbook/` → 지시문 붙여넣기 → 마지막 줄 확인. ⚠·❌ 가 챕터 0~6 범위에서 0 이면 챕터 7 "시작".
