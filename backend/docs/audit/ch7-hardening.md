# 챕터 7 보강 — 잠금 순서 · 재집계 API · 배송 정보 · 상태 이름

정본 `backend/docs/textbook/ch7-hardening.md`(ch7.md 와 충돌하면 이 문서가 이긴다) · 2026-09-06
교체·삭제한 원본은 `backend/docs/audit/ch7-replaced/`

## 0. STEP 0 결과 — 기존 동시성 테스트는 다짐 제출 경로가 아니었다

| 기존 테스트 | 무엇을 동시에 돌렸나 | 다짐 제출 경로인가 |
|---|---|---|
| `StampLockOrderIntegrationTest.concurrent_mission_and_review_do_not_deadlock` | 다짐 제출 1 + **관리자 승인** 1 | 절반만 — 두 요청이 서로 다른 경로다 |
| `CompletionServiceTest.concurrent_recount_does_not_deadlock` | `afterStampCompleted` 직접 호출 ×2 | 아니다 — HTTP·도장 잠금을 거치지 않는다 |

그래서 B-2 두 케이스를 **추가**했다(기존 둘은 그대로 둔다).

**B-1 잠금 순서는 어긋나 있었다.** 다짐 제출 경로가 잡던 순서는
`stamp → 도장 보상 → completion → certificate → 코스 보상` 이었다 — 보상이 완주보다 앞이고, user 칸이 없었다.

```java
// StampService.submitMission
expireOrThrow(stampId);                      // ★ 잠그기 전에 (챕터 5)
userService.lockForCompletion(userId);       // ① user   ← 이번에 넣은 칸
Stamp stamp = lockOwned(userId, stampId);    // ② stamp/slot
...
// CompletionService.afterStampCompleted
pilgrimageMapper.markCompleted(pilgrimageId);        // ③ completion
certificateService.issueForPilgrimage(...);          // ④ certificate
rewardService.grantOrRestore(...);                   // ⑤ reward  ← 맨 뒤로 옮김
```

관리자 심사 경로는 커밋 뒤(AFTER_COMMIT) 새 트랜잭션이라 거기서도 `lockForCompletion` 부터 잡는다.
두 경로가 같은 순서를 쓰므로 원형 대기가 생기지 않는다.

### B-2 결과 (실제 MySQL · 각 5회 반복)

```
MissionConcurrencyIntegrationTest   10건(2케이스 × 5회) 전부 통과
교착 0 · 예외 0 · 5xx 0 · 인증서 번호 중복 0
케이스 1  두 사람이 동시에 마지막 다짐 → 각자 완주 1 · 인증서 1 · 코스 보상 1
케이스 2  한 사람이 두 자리에 동시에 다짐 → 인증서는 많아야 한 장, 결과가 찢어지지 않음
```

**케이스 2 는 교재의 기대와 다르게 잡았다.** 잠금 순서를 넣으면 두 요청이 한 줄로 서고,
뒤에 온 쪽은 **방금 찍힌 도장**을 직전 도장으로 보게 되어 이동시간 규칙에 걸린다 —
같은 사람이 같은 분(分)에 두 절을 끝낼 수는 없으므로 그것이 옳은 동작이다.
`site_distance` 의 `CHECK (min_minutes > 0)` 때문에 이동시간을 0으로 두어 우회할 수도 없다.
그래서 "둘 다 통과" 대신 **"완료 4면 인증서 0, 완료 5면 인증서 1 — 중간 상태 없음"** 을 본다.
교재가 이 케이스로 잡으려던 것(5/5 가 두 번 성립해 인증서가 두 장 나오는 것)은 그대로 막힌다.

덤으로, 이 테스트가 **전체 실행에서만** `COURSE-4001` 로 깨졌다 — 다른 테스트가 `slot_site` 후보를
지웠다 다시 넣는 사이에 걸린 것이라, 전제를 스스로 세우도록 `INSERT IGNORE` 를 setUp 에 넣었다.

---

## 1. A절 정정 — 대조와 판정

| 정정 | 저장소는 어땠나 | 판정 |
|---|---|---|
| §4-2 상태 이름은 저장소 것 | 이미 `GRANTED/CLAIMED/PAID/REJECTED/REVOKED` | **유지** · 교재 이름이 코드·테스트·컬렉션에 남아 있지 않은 것을 grep 으로 확인(§3) |
| 발송은 별도 상태 없이 `PAID` 전이 때 송장 문자열(0~100) | 송장을 담을 곳이 없었다 | **신규** — `user_reward.tracking_no` + `ClaimReviewRequest.trackingNo`. 승인일 때만 남기고 반려에 실려 오면 버린다 |
| §2-3 ① 다짐 제출 재집계는 도장 트랜잭션 **안** | 이미 안에 있다 | **유지** — 응답에 인증서·보상을 실어야 하므로. 대신 B-1·B-2 |
| §2-4 재승인 시 보상은 **같은 행 복귀** | 멱등 키가 재적립을 막아 **REVOKED 인 채 남았다** | **신규** — `grantOrRestore()`. 인증서는 새 번호, 보상은 같은 행 |
| §3-3 `CERT-4041`·`CERT-4040` 중 하나만 | 저장소에는 **`CERT-4041` 하나뿐**이고 `CERT-4040` 은 존재한 적이 없다 | **유지** — 지울 것이 없다. 교재의 `CERT-4040` 표기를 `CERT-4041` 로 읽는다(§4) |
| §3-1 중간본(INTERIM) | 인증서 종류에 없다(`cert_type CHECK` = PILGRIMAGE·HOEHYANG). 중간본은 **전자책**이다 | **제외 확정** — 코드·W 채점에서 빼고 그 사실을 기록 |
| §0 엔드포인트 3개 신설 허용 | 없었다 | **신규 3개** — 아래 §2 |

---

## 2. B-3 신설 엔드포인트 3개 (73 → 76)

| 경로 | 인증 | 규칙 | 확인 |
|---|---|---|---|
| `POST /api/admin/completions/recount/{userId}` | ADMIN | §2-2 네 분기, 응답 `{created, canceled, noop}` | W23·W23b·W23c(3회 연속) |
| `POST /api/admin/completions/recount` | ADMIN | **사용자별 각각 트랜잭션**, 응답 `{scanned, created, canceled, failed[]}` | W24 |
| `POST /api/admin/certificates/{id}/revoke` | ADMIN | `reason` 1~200 필수, 사유 코드 `ADMIN`, 이미 REVOKED 면 409 `CERT-4091` | W25·W25b·W25c·W25d |

**배치가 왜 다른 클래스인가.** 같은 클래스 안에서 `recount()` 를 부르면 프록시를 거치지 않아
트랜잭션이 열리지 않는다 — 전부 한 트랜잭션이 되어 한 명이 실패할 때 앞사람들의 결과까지 되돌아간다.
`CompletionBatchService` 를 따로 둔 이유가 그것뿐이며, 그 사실을 클래스 주석에 적었다.

`CERT-4091` 은 이번에 추가했다. 교재 §3-3 표의 코드인데 **관리자 회수 경로가 생기면서 비로소 던질 자리가 생겼다** —
연쇄(자동) 회수는 이미 회수된 것을 만나도 0행으로 조용히 넘어가야 하지만, 사람이 누른 회수는 알려 줘야 한다.

`endpoint-scan.js` 재실행: **엔드포인트 76 · 컬렉션이 부르는 것 74 · 미검증 2**(둘 다 인쇄 주문 — 챕터 9).

---

## 3. B-5 상태 이름 — 남은 곳 확인

```
grep -rn "CLAIM_REQUESTED|SHIPPED" src backend/docs postman  (textbook 제외)
```

보상 도메인에는 **한 곳도 남지 않았다.** 걸린 것은 전부 `print_order`(인쇄 주문 상태 `REQUESTED→CONFIRMED→SHIPPED→CANCELED`,
챕터 9 범위)와 챕터 7 보고서의 대조표 문장이다. 인쇄 주문의 `SHIPPED` 는 다른 도메인의 같은 낱말이라 그대로 둔다.

정리.md §4 는 저장소 이름으로 다시 썼다(§4-12) — 상태 기계·허용 전이·`claimable` 계산식·
claim 요청 4필드·verify 응답 7필드·재성립 규칙(인증서 새 번호 / 보상 같은 행 복귀).

---

## 4. B-4 배송 정보

`reward_claim` 표를 새로 만들고 `POST /api/rewards/{id}/claim` 이 본문으로 받는다.

```json
{ "recipientName": "…", "phone": "010-0000-0000", "address": "…", "memo": "…(선택)" }
```

- 본체(`user_reward`)와 나누는 이유는 하나다 — **목록 API 가 본체만 읽기 때문이다.**
  주소를 본체에 두면 "내 보상 목록" 한 번에 주소가 따라 나가고, 그 응답은 화면·로그·캐시를 탄다.
- 보상 하나에 배송 정보 하나(`uk_reward_claim`). 반려 뒤 다시 신청하면 **덮어쓴다** — 주소가 바뀌어서
  다시 신청하는 경우가 있다. 지우고 넣지 않고 UPSERT 다(그 사이 "신청은 있는데 주소가 없는" 상태를 만들지 않으려고).
- 배송 정보가 실리는 **유일한 응답은 관리자 심사 목록**이다. 그래서 사용자용 `RewardRow` 와 다른
  `AdminClaimRow` 를 따로 뒀다 — 같은 DTO 를 쓰면 언젠가 사용자 목록 질의가 주소를 조인하게 된다.

확인: 컬렉션 W26(누락 400)·W11(정상 200)·W26b(목록에 주소 없음)·W22d(심사 목록에는 있음),
SQL 채점 ⑯ `claim_rows = 1` · ⑰ `address_columns_in_user_reward = 0`.

---

## 5. 스키마 변경 2건 (실행함)

`backend/docs/verify/alter-ch7.sql` 뒤에 이어 붙였다. `schema.sql` 에도 반영했다.

| # | 무엇 | 왜 |
|---|---|---|
| ④ | `user_reward.tracking_no VARCHAR(100) NULL` | 발송을 별도 상태로 두지 않고 승인 전이 때 함께 받는다 |
| ⑤ | `reward_claim` 표(신규) | 배송 정보를 본체에서 떼어 놓는다 |

`reward_claim` 은 신규 표라 `CREATE TABLE IF NOT EXISTS` 가 그대로 만들지만,
`tracking_no` 는 이미 있는 표라 ALTER 가 필요하다 — 운영에서는 이 파일을 한 번 돌리면 둘 다 해결된다.

**정리 순서 주의**: `reward_claim` 이 `user_reward` 를 RESTRICT 로 잡는다.
`cleanup.sql` 과 JUnit 의 정리 코드 일곱 군데에서 **`user_reward` 보다 먼저** 지우도록 고쳤다.
빠뜨리면 정리가 FK 1451 로 멈추고, mysql 은 첫 오류에서 끝나므로 그 뒤 줄이 통째로 안 돈다(챕터 7 에서 겪은 함정).

---

## 6. STEP 6 — ch7-completion.md §7 의 9번째 [질문] 다시 보고

- **항목**: 인증서 "없음" 404 코드가 교재(§3-3)는 `CERT-4040`, 저장소는 `CERT-4041` 로 서로 다르다.
- **현재 상태**: 저장소에 `CERT-4040` 은 **존재한 적이 없다.** `CERT-4041` 하나로 챕터 0~6 부터 나가고 있고,
  컬렉션(R11·W06)과 DTO 주석이 그 번호를 쓴다. 이번 보강에서 지울 대상이 없어 그대로 두었다.
- **네 권고**: **`CERT-4041` 을 정본으로 두고 교재 §3-3 의 표기를 고치는 쪽**을 권한다.
  번호는 이미 클라이언트가 분기하는 키로 나갔고, 바꾸면 나간 앱이 같은 상황에서 다른 코드를 받는다.
  다만 이것은 명세 §7 원본을 봐야 확정된다 — 명세에 `CERT-4040` 만 있다면 반대로 저장소를 고쳐야 하고,
  그때는 코드·컬렉션·문서 세 곳을 한 번에 옮기고 한 챕터 동안 둘 다 받는 기간을 두는 편이 안전하다. **(결정하지 않음)**

---

## 7. 검증 숫자

```
JUnit     187 / 187        (챕터 7 보강 신규 10 — 다짐 제출 동시성 2케이스 × 5회)
newman    본 실행 315요청 / 1438단언 · 회향 32요청 / 168단언 — 2회 연속 같은 수, 실패 0
엔드포인트 76 · 컬렉션이 부르는 것 74 · 미검증 2 (인쇄 주문 GET·DELETE — 챕터 9)
런타임    ERROR 0 · 스택트레이스 0 · 5xx 0 · WARN 397 중 허용목록 밖 0
매퍼      매퍼↔XML 0(양방향) · #{}↔@Param 0 · 별칭 미채움 0  (select 77개)
보안      16항 통과 · 실패 0
DB        H1~H12 전부 통과 — H3 은 10분을 실제로 기다렸고, H12 는 세 제약 모두 실측으로 막혔다
SQL 채점  배송 정보 1행 · 본체에 주소 컬럼 0 · 회수 사유 ADMIN 1 / COMPLETION_CANCELED 2
```

허용 목록에 두 줄을 더했다 — 완주 취소 연쇄와 **관리자 회수**가 남기는 WARN 이다.
되돌릴 수 없는 일이라 일부러 WARN 으로 찍으므로, 안 찍히는 쪽이 오히려 문제다.

H9(시드 멱등)의 `site_element` 가 344 로 나오는데, 이 db-check 은 **정리 전**에 돌렸기 때문이다
(H12 가 복제할 원본 행을 필요로 한다). 시드 정본은 342 이고 검증 데이터 2행이 얹힌 값이다.
표 수는 33 — 챕터 7 의 `cert_serial` 과 보강의 `reward_claim` 이 더해졌다.

### 확인포인트 C절

| W | 무엇을 보나 | 결과 |
|---|---|---|
| W23·W23b·W23c | 재집계 3회 — 2·3회차 `created:0 canceled:0` | ✅ |
| W23d | 재집계 뒤에도 인증서는 한 장 | ✅ |
| W24 | 배치 재집계 — `failed:[]` | ✅ |
| W25·W25b·W25c | 회수 사유 없이 400 → 사유 있으면 200 → 다시 회수 409 `CERT-4091` | ✅ |
| W25d | 회수된 번호도 진위 확인 200 + REVOKED, 사유는 안 실림 | ✅ |
| W26 | 배송 정보 없이 신청 → 400(어느 필드인지 알려 준다) | ✅ |
| W11·W26b | 정상 신청 200 CLAIMED · 목록 응답에 주소·이름·연락처·메모 없음 | ✅ |
| W27 | 재성립하면 회수됐던 보상이 **같은 id 로** GRANTED 복귀, 회수 상태로 남은 것 0 | ✅ |
| W28·W28b | 비관리자 재집계·회수 → 403 | ✅ |
| W22d | 심사 목록은 `needs_review` 가 맨 위, 배송 정보가 실린다 | ✅ |

W27 의 단언은 처음에 "보상 행 수가 늘지 않았다" 로 썼다가 고쳤다 — 재승인으로 **회향이 성립하면서
회향 보상이 새로 붙는 것이 정상**이라 전체 행 수는 늘 수 있다. 봐야 할 것은 "그 보상이 같은 id 로
한 줄만 있는가" 와 "회수된 채 남은 것이 없는가" 다.

---

newman 347/347, 기능 ✅ 11 · ⚠ 0 · ❌ 0 · — 1
