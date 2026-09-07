# 챕터 7 보강 — 정본 정정 및 추가 규칙

작성 2026-09-06 · `ch7.md`에 대한 정정·추가. 충돌하면 이 문서가 이긴다. 챕터 5 보강과 같은 형식.

## A. ch7.md 정정

| 위치 | 정정 |
|---|---|
| §4-2 상태 기계 | 상태 이름은 **저장소 이름**을 쓴다: `GRANTED → CLAIMED → PAID`, 반려 `REJECTED`, 취소 `REVOKED`. 교재의 CLAIM_REQUESTED·APPROVED·SHIPPED는 각각 CLAIMED·PAID·(없음)로 읽는다. 발송 단계는 별도 상태 없이 `PAID` 전이 시 송장 문자열(선택, 0~100자)만 받는다 |
| §2-3 ① | 다짐 제출(현장 인증 3단계 마지막) 경로의 재집계는 **도장 트랜잭션 안에 둔다**(응답에 인증서·보상을 실어야 함). 대신 아래 B-1 잠금 순서와 B-2 동시성 테스트가 필수. 관리자 승인·반려 경로는 AFTER_COMMIT + REQUIRES_NEW 유지 |
| §2-4 재승인 | 완주 재성립 시 **보상은 같은 행 REVOKED → GRANTED 복귀**(멱등 키가 재적립을 막으므로). 인증서는 새 번호 — 둘의 차이를 정리.md §4에 명기 |
| §3-3 | `CERT-4041` 을 정본으로 확정(2026-09-06). 저장소에 `CERT-4040` 은 존재한 적이 없어 삭제할 것도 없다 — 명세 §7 에 4040 만 있으면 명세를 4041 로 고친다 |
| §3-1 중간본 | 저장소에 중간본(INTERIM) 구현이 없으면 **제외**로 정정(§2-5 "중간본 성립" 줄 삭제, W 채점에서 제외). 있으면 유지 |
| §0 엔드포인트 | 이 보강에 한해 **3개 신설 허용**: 관리자 재집계 1명·배치, 관리자 회수. endpoints.md 73 → 76. 그 외 신설 금지 유지 |

## B. 추가 규칙

**B-1 잠금 순서(다짐 제출 경로)** — 한 요청 안에서 잠그는 순서를 고정한다: `user 행(또는 user 단위 락) → stamp/slot 행 → completion 행 → certificate/reward`. 같은 순서로만 잡으면 두 요청이 서로를 기다리는 원형이 생기지 않는다. 순서를 문서(정리.md §6)에 적고 코드 주석에도 남긴다.

**B-2 동시성 JUnit(실제 MySQL)** — `CompletionServiceTest`에 다짐 제출 경로로 두 케이스 추가, 각각 2스레드 동시 실행 5회 반복, 교착·예외 0, 결과 행 수 정확:
1. 같은 코스·다른 사용자 2명이 동시에 5번째 다짐 제출 → 완주 2건·인증서 2장(번호 중복 0)·보상 각 1
2. 같은 사용자가 다른 슬롯 2개에 동시에 다짐 제출(4/5 → 둘 다 통과해도 5/5는 1번만) → 완주 1건·인증서 1장
기존 "동시 2스레드 교착 0"이 관리자 승인 경로였다면 그 테스트는 그대로 두고 위 둘을 **추가**한다.

**B-3 신설 엔드포인트 3개**

| 경로 | 인증 | 규칙 |
|---|---|---|
| `POST /api/admin/completions/recount/{userId}` | ADMIN | ch7.md §2-2 4분기, 응답 `{created, canceled, noop}` |
| `POST /api/admin/completions/recount` | ADMIN | 사용자별 각각 트랜잭션, 응답 `{scanned, created, canceled, failed:[userId]}`. 한 명 실패가 전체를 되돌리지 않음 |
| `POST /api/admin/certificates/{id}/revoke` | ADMIN | body `reason` 1~200 필수, 사유 코드 `ADMIN`, 이미 REVOKED면 409 |

**B-4 배송 정보** — `reward_claim` 표 신설(reward_id FK 유니크, recipient_name 1~50, phone, address 1~200, memo 0~200, created_at). `POST /api/rewards/{id}/claim` body로 받고 `@Validated`. `reward` 본체·목록 API에는 절대 싣지 않는다. 관리자 심사 API 응답에만 포함. ALTER는 alter-ch7.sql에 append, 정리.md §5-1 갱신.

**B-5 정리.md §4 프론트 전달** — 실제 상태 이름으로 다시 쓴다: 보상 상태 기계(GRANTED/CLAIMED/PAID/REJECTED/REVOKED + 허용 전이), `claimable` 계산식(`PHYSICAL AND status IN (GRANTED, REJECTED) AND 본인`), claim 요청 body 4필드, verify 응답 필드 7개, 재성립 시 "인증서 새 번호·보상 같은 행 복귀".

## C. 확인포인트 추가

| W | 요청 | 기대 |
|---|---|---|
| W23 | ADM `recount/{U1}` 3회 | 200, 2·3회차 `created:0 canceled:0` |
| W24 | ADM 배치 recount | 200, `failed:[]` |
| W25 | ADM 회수(사유 없이) → 사유 있음 → 다시 회수 | 400 → 200 → 409 |
| W26 | U1 claim(배송정보 누락) → 정상 | 400 → 200 CLAIMED, `GET /api/rewards` 응답에 address 키 **없음**(SQL로 reward_claim 1행) |
| W27 | 취소→재승인 뒤 `GET /api/rewards` | 같은 reward id가 GRANTED로 복귀, 행 수 증가 없음 |
| W28 | 비ADMIN으로 W23·W25 | 403 |

## D. 닫힘 조건(추가분)

B-2 두 케이스 통과 · W23~W28 통과 · newman 전체 2회 동일 · endpoints.md 76개 · unused 표에 CERT-404x 하나 삭제 반영 · 정리.md §1·§4(재작성)·§5-1·§6(잠금 순서) 갱신 · 마지막 줄 형식 동일.
