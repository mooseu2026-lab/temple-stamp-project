# 전체 점검 E — verify-ch0-8 (정본 · 노션용)

작성 2026-09-07 · 대상 챕터 0~8 + 보강 3건 · 목적: **지금까지 실현한 기능·API·DB 목록**을 한 장으로 고정하고, localhost:8080에서 실제로 도는지 · DB 연결 · 보안 · 에러 · Postman(newman)을 한 번에 실측한다. 점검 B·C·D와 같은 형식. 결과는 §8 템플릿으로 남겨 노션에 그대로 올린다.

---

## 1. 점검 원칙

| # | 원칙 |
|---|---|
| 1 | **"있다"가 아니라 "돈다"** — newman 요청이 실제 응답을 받고 SQL 채점이 맞아야 ✅. 코드만 있으면 ⚠, 없으면 — |
| 2 | **도구부터 검증** — 점검 B·C·D·챕터 7·8에서 도구 결함 12건이 조용히 거짓 통과했다. 이번엔 자가검증 4건 + **기대 개수 단언**(scan = probe = endpoints.md = 86, 하나라도 다르면 실패) |
| 3 | **2회 동일** — run-all.sh 2회의 요청·단언·SQL 채점이 완전히 같아야 멱등 |
| 4 | **정본 대조** — 엔드포인트는 endpoint-scan.js, 표는 information_schema, 기능은 §2 |
| 5 | 코드·스키마를 고치지 않는다. 결함은 보고만. 예외: 점검 도구 결함은 즉시 고치고 재실행, 수정 전 파일 보관 |
| 6 | 챕터 9 코드(전자책·인쇄주문 GET/DELETE·storage_orphan 청소기)는 대상 아님 — 표에 "미검증(ch9)"로만 |

---

## 2. 기능 목록 (F01~F49)

### 챕터 0~7 (F01~F33 — 점검 D와 동일)

| F | 기능 | 챕터 | 폴더 |
|---|---|---|---|
| F01 | 회원가입·이메일/비번 검증·동의 | 1 | A |
| F02 | 로그인 JWT 30분·refresh 회전·재사용 탐지 | 1 | A |
| F03 | 5회 실패 잠금 403·해제 | 1 | A |
| F04 | 회원 탈퇴(소프트 삭제·익명화·연쇄 9단계·도장 사진키/문장 비움) | 1보강 | U |
| F05 | 권역 10행(제주 "준비 중")·코스 목록 ACTIVE만 | 2 | P |
| F06 | 코스 상세·슬롯 5·후보 사찰·congested | 2·4 | P |
| F07 | 사찰 싱글페이지 8블록·가는 법 7칸 | 3 | P |
| F08 | 관리자 사찰 UPSERT·status 필터·좌표 화이트리스트 | 3 | M |
| F09 | 카카오 장소 연동 | 3 | K |
| F10 | 순례 시작·여권·진행률(progressOf 한 곳) | 4 | G |
| F11 | 현장 인증 1단계 GPS(서버는 좌표 미수신) | 5 | S |
| F12 | 2단계 QR(만료 없음·회전 무효화) | 5 | S |
| F13 | 3단계 미션·다짐 제출 → 도장 | 5 | S |
| F14 | 세션 60분 만료(JUnit T-08 실측 인정) | 5 | S |
| F15 | 하루 한도 5·예외접수 2·관리자 승인 한도 무관 | 5 | S |
| F16 | 슬롯 1도장·409 + by-slot | 5 | S |
| F17 | 관리자 도장 심사(COMPLETED↔REJECTED) | 5·7 | M |
| F18 | presign(jpeg/png·키 소유 검증) | 6 | C |
| F19 | 사진 사찰당 1 + 문장 1 UPSERT | 6 | C |
| F20 | 생각상자(isPrivate)·flashback 200+null | 6 | T |
| F21 | 명상 기록 | 6 | V |
| F22 | 완주 판정·completion·재집계 멱등 | 7 | W |
| F23 | 관리자 재집계 1명·배치 | 7보강 | W |
| F24 | 완주 취소 연쇄(인증서 회수·보상 GRANTED만 REVOKED·needs_review) | 7 | W |
| F25 | 인증서 자동 발행·시퀀스 번호·중복 0 | 7 | W |
| F26 | 인증서 관리자 회수·재발행 새 번호 | 7보강 | W |
| F27 | 공개 진위 확인(비로그인·마스킹·회수 200+REVOKED) | 7 | W |
| F28 | 보상 적립 멱등·claimable 서버 계산 | 7 | R·W |
| F29 | 수령 신청(reward_claim 분리) | 7보강 | R·W |
| F30 | 관리자 보상 심사(CLAIMED→PAID·REJECTED) | 7 | R·W |
| F31 | 회향 하한 12·HH 인증서 | 7 | W |
| F32 | 완주 재성립 시 보상 같은 행 복귀 | 7보강 | W |
| F33 | 공통: ErrorCode 형식·@Validated 400·404 존재 미노출 | 1~7 | X |

### 챕터 8 (F34~F49)

| F | 기능 | 폴더 |
|---|---|---|
| F34 | 원고 등록(DRAFT)·variantNo 서버 배정 | N |
| F35 | 변형 상한 3(설정값)·기본 원고 1편 | N |
| F36 | 금칙(URL·전화·이메일·HTML) 400 MS-4002 | N |
| F37 | 본문 동일 중복 409 MS-4091 | N |
| F38 | 작성자 수정(DRAFT/REJECTED·제목·본문만)·남의 원고 404 | N |
| F39 | 제출·상태 전이(표 밖 MS-4090) | N |
| F40 | 심사 4-eyes(자기 원고 403 MS-4030) | N |
| F41 | 반려 사유 필수·REJECTED→수정 시 DRAFT(사유 이력) | N |
| F42 | 승인·기본 원고 승인 시 이전 기본 RETIRED | N |
| F43 | 퇴역(삭제 없음) | N |
| F44 | 미션 원고 선택 userId mod count·세션 고정 | N |
| F45 | 기본 원고 fallback·전부 없으면 MS-4093 | N |
| F46 | 확장문구 발행 시 stamp 고정·extPhrase 필드·퇴역 뒤 옛 도장 유지 | N |
| F47 | CSV 반입(원자성·dryRun·시군구 대조·파일 내 상한 합산·BOM·2MB/1,000행) | N |
| F48 | EDITOR 역할·/api/editor/** 보호 | N·X |
| F49 | 잠금 규칙("있는 행" 잠금) — 동시 등록 6스레드 교착 0 | JUnit |

---

## 3. 엔드포인트 (86개 · endpoint-scan.js가 정본)

`node endpoint-scan.js`로 컨트롤러를 전수 스캔해 `docs/audit/endpoints.md`를 덮어쓴다. 아래는 **도메인별 예상 묶음** — 실제 개수가 다르면 스캔이 이기고 차이를 보고.

| 도메인 | 경로군 | 인증 | 예상 |
|---|---|---|---|
| 인증·회원 | `/api/auth/**` `/api/users/me` | 없음/USER | 8 |
| 권역·코스·사찰(조회) | `/api/regions` `/api/courses/**` `/api/sites/**` | 없음 | 9 |
| 카카오 | `/api/kakao/**` | USER | 2 |
| 관리자 사찰·도장 심사·사용자 | `/api/admin/sites/**` `/api/admin/stamps/**` `/api/admin/users` | ADMIN | 10 |
| 순례·여권 | `/api/pilgrimages/**` | USER | 4 |
| 현장 인증·도장 | `/api/stamps/**` (gps-check·qr·mission·pledge·by-slot·exception) | USER | 9 |
| 기록 | `/api/uploads/presign` `/api/photos/**` `/api/thinkbox/**` `/api/flashback` `/api/meditations/**` | USER | 9 |
| 완주·인증서·보상 | `/api/completions` `/api/certificates/**`(verify 비로그인) `/api/rewards/**` | USER/없음 | 8 |
| 관리자 완주·인증서·보상 | `/api/admin/completions/**` `/api/admin/certificates/**` `/api/admin/rewards/**` | ADMIN | 9 |
| 원고(편집자) | `/api/editor/manuscripts/**` | EDITOR+ | 4 |
| 원고(관리자) | `/api/admin/manuscripts/**` | ADMIN | 5 |
| 인쇄주문(챕터 9) | `/api/print-orders/{id}` GET/DELETE | USER | 2 (미검증 ch9) |
| 기타(health 등) | `/actuator/health` 등 | 없음 | 7 |
| **합계** | | | **86** |

판정: newman 요청 1개 이상 통과 → ✅ / 코드만 → ⚠ / 문서에만 → ❌. **집계 줄** `엔드포인트 86 · ✅ a · ⚠ b(ch9 c) · ❌ d`, b−c = 0이어야 통과.
비로그인 실측: `open-endpoint-probe.js` — 허용 n · 보호 m · **n+m = 86 단언** · 어긋남 0.

---

## 4. DB 표 (information_schema가 정본)

`SELECT table_name, table_rows, table_comment FROM information_schema.tables WHERE table_schema='temple_stamp_project'`로 뽑아 아래 묶음과 대조. 예상 **38표**(점검 C 31 + ch7 5 + storage_orphan + 시퀀스). 다르면 실제가 이기고 차이를 보고.

| 묶음 | 표 |
|---|---|
| 회원·인증 | user · refresh_token · login_fail · consent(동의) |
| 지리·콘텐츠 | region · course · course_site(자리 60) · slot_site(후보) · site(사찰 110) · site_element(참배요소 342) · site_access(가는 법) · kakao_place |
| 순례·도장 | pilgrimage · stamp(원고 두 칸 포함) · stamp_session · qr_secret · stamp_exception |
| 기록 | photo · thinkbox · meditation · storage_orphan |
| 완주·인증서·보상 | completion · certificate · cert_serial · reward_rule · user_reward · reward_claim |
| 원고 | manuscript(site_key 생성 컬럼·조건부 유니크) |
| 시더·기타 | seed_version 등 |

DB 점검 `db-check.sh` **H1~H15**(H13 챕터 7 표 정합·H14 ALTER 14건 전부 적용·H15 기본 원고 10편 APPROVED) + **H5 판정 수정**(실행 시작 시각 이후 교착 기록만). 추가 실측: 시드 숫자 불변(사찰 110·코스 12 DRAFT·자리 60·후보 213+5·요소 342·region 10·원고 10)·좌표(0,0)=1·인증서 번호 중복 0·`@@time_zone=+09:00`·HikariCP 활성/대기 스냅샷.

---

## 5. localhost:8080 기동 · 런타임

| 항목 | 기대 |
|---|---|
| `bootRun` 새로 시작 | 기동 로그 ERROR 0 · WARN 허용목록 밖 0 · 기동 시간 기록 |
| `GET /actuator/health` | `UP`, db `UP` |
| 시더 | 재기동 시 시드 멱등(숫자 불변) |
| newman 2회 동안 | ERROR 0 · 5xx 0 · 스택트레이스 응답 0 |
| 종료 | 청소기 스케줄러 정상 종료, 커넥션 풀 반환 |

---

## 6. 보안 (S1~S18 + 신규)

| S | 내용 |
|---|---|
| S1~S12 | 챕터 1 기존(JWT·잠금·CORS·헤더·경로 등) |
| S13 | 비로그인 허용 목록 = 실측, 그 외 401 (+editor·admin/manuscripts 경로군) |
| S14 | ADMIN 경로 비ADMIN 403 · EDITOR 경로 USER 403 |
| S15 | verify 응답 개인정보 키 부재 |
| S16 | 보상 목록에 배송정보 부재 |
| S17 | CoordinateFieldGuard 음성 |
| S18 | refresh 재사용 시 전체 세션 무효 |
| S19 | 탈퇴 후 옛 access·refresh 전부 401 |
| S20 | DRAFT·REJECTED·RETIRED 원고 본문이 사용자 응답에 새로 나가지 않음 |
| S21 | 원고 금칙 4종 400(전화·URL·이메일·HTML) |
| S22 | 관리자 사용자 목록 DELETED 기본 제외 |

---

## 7. 실행 순서

1. **도구 자가검증** — cleanup FK 위반 / mapper 양방향 / H12 0행 / 스캔 임시 메서드 → 4/4 잡힘. **+ 기대 개수 단언 확인**(scan 86 = probe 86 = endpoints.md 86; 하나를 85로 만들면 실패해야 함)
2. `bootRun` 재기동 → health → 시드 멱등
3. `endpoint-scan.js` → endpoints.md → `open-endpoint-probe.js`(86 단언)
4. JUnit 전체(실제 MySQL) — 개수·실패 0·동시성(승인 2·다짐 2·원고 등록 1·인증서 발행 1) 교착 0
5. `run-all.sh` 1회차 (A·U·P·M·G·K·S·C·T·V·R·W·N·X)
6. runtime·security(S1~S22)·db-check(H1~H15)·시드·time_zone·풀 스냅샷
7. `run-all.sh` 2회차 → 1회차와 완전 일치
8. 기능 F01~F49 판정 → 결과 문서(§8 템플릿) → 정리.md §1·결과.md·제시.md 갱신

---

## 8. 결과 문서 템플릿 (노션 Import용 — `docs/audit/verify-ch0-8-결과_노션용.md`)

아래 뼈대를 **그대로** 쓰고 숫자만 채운다. 코드블록·중첩 목록 없이 표와 문단만(노션 Import에서 깨지지 않게).

```
# 전체 점검 E 결과 — 챕터 0~8 (YYYY-MM-DD)

## 한 줄 요약
verify-E: API 86(✅a ⚠b ❌c) · 기능 ✅a ⚠b ❌c · newman N/N×2 · JUnit N/N · DB H15/15 · 보안 S22/22 · 도구 4/4

## 1. 도구 자가검증
| 도구 | 심은 결함 | 잡힘 | 기대 개수 단언 |
(4행)

## 2. 기동·런타임 (localhost:8080)
| 항목 | 결과 |
기동 시간 · health · 시드 멱등 · ERROR · 5xx · 스택트레이스 · WARN 허용목록 밖

## 3. 엔드포인트 86
| 도메인 | 개수 | ✅ | ⚠ | ❌ |
(§3 묶음별) + 집계 줄 + 비로그인 표(허용 n·보호 m·합 86·어긋남 0)

## 4. DB
| H | 내용 | 결과 |  (H1~H15)
| 표 | 행 수 | 비고 |  (38표 전수)
시드 숫자 · 좌표(0,0) · 번호 중복 · time_zone · 풀 스냅샷

## 5. 보안
| S | 내용 | 결과 |  (S1~S22)

## 6. Postman(newman)
| 폴더 | 요청 | 단언 | 실패 | 2회 일치 |
(A~X 14폴더) + 합계

## 7. JUnit
| 클래스 묶음 | 건수 | 실패 |  + 동시성 6건 표

## 8. 기능 F01~F49
| F | 기능 | 판정 | 근거(폴더·요청 id) | 비고 |
(49행 전부)

## 9. 결함
### 제품 결함 (고치지 않고 보고)
| # | 무엇 | 챕터 | 성격 |
### 도구 결함 (이번에 고침)
| # | 무엇 | 고침 |

## 10. [질문]
번호 · 항목 · 현재 상태 · 권고 (결정하지 않음)

## 마지막 줄
verify-E: API 86(✅a ⚠b ❌c) · 기능 ✅a ⚠b ❌c · newman N/N×2 · JUnit N/N · DB H15/15 · 보안 S22/22 · 도구 4/4
```

**챕터 9 진입 조건**: 기능 ⚠(ch9 제외) 0 · 엔드포인트 ⚠(ch9 제외) 0 · 2회 동일 · 5xx 0 · S1~S22 · H1~H15 · 도구 4/4 + 개수 단언.
