# 챕터 7 — 완주 재집계 · 인증서 · 보상 · 회향

정본 `backend/docs/textbook/ch7.md` · 2026-09-06 · 교체·삭제한 원본은 `backend/docs/audit/ch7-replaced/`

> **보강 반영(2026-09-06).** 이 문서 뒤에 `ch7-hardening.md` 가 있다. 아래 [질문] 아홉 중
> **1·3·5 는 보강에서 닫혔고**(재집계 API 2개·관리자 회수 1개 신설, `reward_claim` 표, 송장 문자열),
> **4 는 "제외" 로 확정**(중간본은 이 저장소에서 전자책이다), **7 도 닫혔다**(보상은 같은 행 복귀).
> 남은 것은 2(관리자 회수는 열렸으나 회원 탈퇴 사유는 여전히 없음)·6·8·9 다.

이 챕터의 코드는 이미 있었다. 한 일은 **규칙과 저장소를 한 줄씩 대조해 판정하고, 어긋난 곳을 고치고,
종단 검증을 붙인 것**이다. 낱말이 다른 곳이 많아 대조표를 먼저 둔다.

## 0. 저장소의 낱말 ↔ 교재의 낱말

교재의 이름으로 코드를 고치지 않았다. 이름을 바꾸면 프론트·명세·다른 챕터가 함께 흔들리는데,
그 값이 없다. 대신 **어느 것이 어느 것인지** 를 여기 한 번 적어 두고 그대로 쓴다.

| 교재 | 저장소 | 비고 |
|---|---|---|
| `completion` 표 (완주 기록) | **`pilgrimage.status = COMPLETED`** | 유니크 `(user_id, course_id)` 는 `uk_pilgrimage` 로 **이미 있다** → ALTER 후보 아님 |
| 완주 취소 = `status CANCELED` | `pilgrimage.status = IN_PROGRESS` | 행을 지우지 않는 것은 같다 |
| `reward_rule` | `reward_policy` | 종류는 `STAMP / COUPON / PHYSICAL` |
| `DIGITAL` 보상 | `STAMP`·`COUPON` (= PHYSICAL 이 아닌 것) | 수령 신청 대상이 아니다 |
| `CLAIM_REQUESTED` | **`CLAIMED`** | DB `CHECK` 에 이미 있던 값을 그대로 썼다 |
| `APPROVED` | **`PAID`** | 승인 = 지급 확정 |
| `SHIPPED`(발송) | **없음** | 송장·발송 단계가 저장소에 없다 → §5 [질문] |
| `INTERIM` 인증서(중간본) | **없음** — 중간본은 `ebook` 이다 | `cert_type` 은 `PILGRIMAGE`·`HOEHYANG` 둘뿐 → §5 [질문] |
| `CERT-4040` | **`CERT-4041`** | 번호는 바꾸지 않는다(절대 규칙). 뜻은 같다 |

---

## 1. §2 완주 · 재집계 — 대조와 판정

| 규칙 | 저장소는 어땠나 | 판정 |
|---|---|---|
| §2-1 완주 판정은 `progressOf` 재사용, 별도 SQL 금지 | `stampMapper.countCompleted` 로 **따로 세고 있었다**(내용은 같았지만 출처가 둘) | **교체** — `PilgrimageService.progressOf` 를 쓰도록 바꿨다. 두 곳이 같은 SQL이어도 출처가 둘이면 한쪽만 고쳐질 날이 온다(함정 4) |
| §2-2 5/5 인데 기록 없음 → 생성 | 이미 그렇다 | 유지 |
| §2-2 5/5 이고 기록 있음 → 아무것도 안 함 | 이미 그렇다(멱등) | 유지 · 테스트로 못 박음(`recount_is_idempotent`) |
| §2-2 5/5 아닌데 COMPLETED → 취소 + 연쇄 | 되돌리기는 있었으나 **연쇄가 인증서 삭제뿐**이었다 | **교체** — 아래 §2-4 |
| §2-2 유니크 `(user_id, course_id)` | `uk_pilgrimage` 로 이미 있다 | 유지 (ALTER 후보 아님) |
| §2-2 완주·인증서·보상이 한 트랜잭션 | 이미 그렇다 | 유지 |
| §2-3 ① 도장 승인 직후 재집계 | 있다 | 유지 |
| §2-3 ①② **커밋 뒤** 별도 트랜잭션 | 관리자 심사가 **같은 트랜잭션 안**에서 불렀다 | **교체** — `StampReviewedEvent` + `CompletionEventListener`(`AFTER_COMMIT` + `REQUIRES_NEW`) |
| §2-3 ③ 관리자 재집계 API(1명/배치) | **엔드포인트 없음**(endpoints.md 73개) | **부재로 보고** — 신설 금지 규칙에 따라 만들지 않았다. §5 [질문] 1 |
| §2-4 1 코스 인증서 → REVOKED | **행을 DELETE 하고 있었다** | **교체** — 상태 컬럼을 만들고 회수로 바꿨다. 함정 3·7 이 여기 걸려 있었다 |
| §2-4 2 GRANTED 만 REVOKED, 나머지 `needs_review` | **보상은 아예 손대지 않았다**("사람이 판단할 일" 이라는 주석과 함께) | **교체** — 규칙대로. 취지는 같았는데 표시가 없어 사람이 알 방법이 없었다 |
| §2-4 3 완주 3 아래 → 중간본 REVOKED | 중간본이 인증서가 아니라 전자책이다. 전자책은 §2-4 5 가 "손대지 말라" 고 한 것 | **부재로 보고** — §5 [질문] 4 |
| §2-4 4 회향 깨지면 회향 인증서·보상 | 없었다 | **신규** — `revokeHoehyang` · `revokeOrFlagByTrigger` |
| §2-4 5 전자책은 손대지 않는다 | 이미 그렇다 | 유지 |
| §2-4 재승인 = 같은 행 되살림 + **새 번호** | 옛 구현은 인증서를 지웠다가 다시 만들어 **번호가 재사용**될 수 있었다 | **교체** — 조건부 유니크 + 시퀀스(§3) |
| §2-5 중간본 3 · 회향 하한 12 | 이미 있다(챕터 5 보강) | 유지 · 하한을 W15·JUnit 두 곳에서 실측 |
| 배치는 사용자별 트랜잭션 · `{scanned, created, canceled, failed[]}` | 배치 자체가 없다(엔드포인트 부재) | **부재로 보고** — §5 [질문] 1 |

**AFTER_COMMIT 을 심사 경로에만 넣은 이유.** 사용자의 다짐 제출(`POST /api/stamps/{id}/mission`)은
응답에 이번 완주의 인증서 번호와 보상 목록을 실어 보낸다(챕터 5 의 응답 계약). 그 경로까지 커밋 뒤로
미루면 응답에 담을 것이 없어진다. 관리자 심사는 응답이 `ApiResponse<Void>` 라 잃을 것이 없다.
그래서 **심사만** 이벤트로 뺐고, 그 자리가 교재가 말한 교착 후보(두 관리자가 동시에 승인)와 정확히 겹친다.

---

## 2. §3 인증서 — 대조와 판정

| 규칙 | 저장소는 어땠나 | 판정 |
|---|---|---|
| §3-1 번호 `{PG\|HH}-yyyy-6자리` | 같다 | 유지 |
| §3-1 `MAX+1`·`COUNT+1` 금지, 시퀀스 사용 | **`COUNT(*)+1` + 충돌 시 재시도** 였다 | **교체** — `cert_serial` 표(신규)에서 `INSERT … ON DUPLICATE KEY UPDATE` 한 문장으로 받는다 |
| 회수한 번호 재사용 금지 | 회수가 DELETE 라 **COUNT 가 줄어 번호가 재사용**될 수 있었다 | 위 교체로 함께 해결. 시퀀스는 되돌리지 않는다 |
| §3-2 `VALID → REVOKED` 한 방향 | 상태 컬럼 자체가 없었다 | **신규** — `certificate.status`·`revoked_at`·`revoke_reason` |
| §3-2 회수 사유 3종 | 없었다 | `COMPLETION_CANCELED` 만 **신규**. `ADMIN`(관리자 회수 엔드포인트 부재)·`USER_WITHDRAWN`(회원 탈퇴 기능 부재)은 부를 자리가 없어 상수도 만들지 않았다 → §5 [질문] 2·6 |
| §3-3 `CERT-4040` 404 | `CERT-4041` | **유지**(번호 변경 금지). 뜻·상황 동일 |
| §3-3 `CERT-4090` 중복 발행 409 | 예외를 던지지 않고 **있던 인증서를 돌려준다**(멱등) | **유지** — 재집계가 반복해서 도는 구조라 여기서 예외가 나면 완주가 통째로 롤백된다. 코드는 미참조로 남긴다(ch7-unused.md) |
| §3-3 `CERT-4091` 재회수 409 | 없다 | **만들지 않음** — 관리자 회수 엔드포인트가 없어 도달 경로가 없다. 연쇄에서의 재회수는 0행(무해)으로 끝난다 → §5 [질문] 2 |
| §3-4 응답 7필드 고정 | 6필드였고 이름이 달랐다(`valid`·`holderNickname`) | **교체** — `serialNo, certType, status, courseName, holderMasked, issuedAt, revokedAt`. 이름만 교재 쪽으로 옮긴 것이 `holderMasked` 다(마스킹된 값임이 이름에서 보인다) |
| §3-4 2자 이름 `남*` | 이미 그렇다 | 유지 · 테스트로 못 박음 |
| §3-4 회수 번호는 200 + REVOKED | **DELETE 였으므로 404** 였다 | **교체**(함정 3) — W18 이 실측 |
| §3-4 verify 가 비로그인 허용 목록에 | 이미 `permitAll(GET)` — `endpoints.md` 30번 | 유지 · `security-check.sh` S1 과 X 폴더가 확인 |
| §3-5 `updateFileKey` 유지 | 미참조 | **유지**(ch9) |

**유효한 것만 유일하게.** `uk_certificate_pilgrimage`(완주 1건당 1장)를 그대로 두면 회수 뒤 재발행이
유니크에 막힌다. 그렇다고 없애면 유효한 인증서가 두 장 생길 수 있다. `stamp.completed_course_site_id` 에서
쓴 수법을 그대로 가져와 **생성 컬럼 + 유니크**로 조건부 유일성을 만들었다.

```sql
valid_pilgrimage_id BIGINT GENERATED ALWAYS AS
    (CASE WHEN status = 'VALID' THEN pilgrimage_id END) STORED,
CONSTRAINT uk_certificate_valid_pilgrimage UNIQUE (valid_pilgrimage_id)
```

---

## 3. §4 보상 — 대조와 판정

| 규칙 | 저장소는 어땠나 | 판정 |
|---|---|---|
| §4-1 멱등 키 | `(정책, 도장)`·`(정책, 순례)` 유니크 + `INSERT IGNORE` | 유지 — 교재의 `(user, source_type, source_id, reward_type)` 과 사실상 같다 |
| §4-1 규칙표 없으면 조용히 통과 | 이미 그렇다(`findByTrigger` 가 빈 목록) | 유지 |
| §4-3 claimable 계산을 서비스 한 곳에서 | **응답 DTO 가 따로 계산**했고(`PHYSICAL && claimedAt == null`) 신청 API 는 아예 다른 검사를 했다 — 결함 5-1 | **교체** — `RewardService.claimable()` 하나. DTO 는 받아 담기만 한다 |
| §4-3 본인 아님 → 404 `REWARD-4040`(또는 기존 403 유지) | 403 `AUTH-4032` | **유지** — 교재가 열어 둔 갈래. 남의 자원은 챕터 1부터 이 코드다 |
| §4-3 DIGITAL → 400 `REWARD-4001` | 검사 없음 | **신규** — 명세 §7 코드의 구현 누락 |
| §4-3 상태 아님 → 409 `REWARD-4092` | `REWARD-4091` 을 던졌다 | **교체** — 교재가 지정한 4092 로. 4091 은 미참조로 남긴다(번호 변경 금지) |
| §4-3 통과 → CLAIM_REQUESTED + `claimable:false` | **바로 지급(PAID)** 되거나 감사 점수가 높으면 UNDER_REVIEW | **교체** — `CLAIMED`(=CLAIM_REQUESTED)로. 감사 점수는 그대로 남긴다(보안 검사 제거 금지) |
| §4-3 배송 정보는 `reward_claim` 별도 표 | **배송 정보를 아예 받지 않는다**(claim 본문이 빈 객체) | **부재로 보고** + ALTER 후보 → §5 [질문] 3 |
| §4-2 반려 뒤 다시 claim 허용 | `WHERE status = 'GRANTED'` 라 막혔다 | **교체** — `GRANTED, REJECTED` 둘 다 허용, 지난 심사 흔적은 지운다 |
| §4-2 불허 전이는 전부 `REWARD-4090` | `COMMON-4090` 을 던졌다 | **교체** |
| §4-4 승인·반려, 반려 사유 1~200 필수 | 사유 필수는 있었고 상한이 300 이었다 | **교체**(200) · 승인은 유지 |
| §4-4 발송(SHIPPED) | 없다 | **부재로 보고** → §5 [질문] 5 |
| §4-4 `REWARD-4030` 을 공통 권한 코드로 대체 가능한가 | 관리자 권한은 `SecurityConfig` 매처가 막고 스프링이 403 을 낸다 — 서비스가 던질 자리가 없다 | **판정: 대체가 아니라 미사용.** 문구도 권한이 아니라 감사 보류다. 명세 코드라 유지 |
| §4-4 `needs_review` 는 항상 맨 위 | 그런 컬럼이 없었다 | **신규** — 컬럼 + `ORDER BY needs_review DESC` |

**심사 목록이 넓어졌다.** 예전에는 `UNDER_REVIEW`(감사 점수가 높은 것)만 관리자에게 보였다.
이제는 접수된 신청(`CLAIMED`)과 `needs_review` 가 켜진 것도 함께 온다 — 그러지 않으면 정상 신청이
아무에게도 안 보인 채 영원히 접수 상태로 남는다.

---

## 4. 스키마 변경 3건 (실행함) · ALTER 후보 3건 → 보강에서 둘 실행

실행한 것은 `backend/docs/verify/alter-ch7.sql` 에 그대로 있고, `schema.sql` 에도 반영했다.
`schema.sql` 은 `CREATE TABLE IF NOT EXISTS` 라 **이미 있는 표에는 아무 일도 하지 않는다** — 그래서 두 벌이다.

| # | 무엇 | 왜 |
|---|---|---|
| ① | `user_reward.needs_review` + `status` CHECK 에 `REVOKED` | §2-4 취소 연쇄 |
| ② | `certificate.status`·`revoked_at`·`revoke_reason` | §3-2 회수는 삭제가 아니다 |
| ③ | `certificate` 유니크를 조건부로(생성 컬럼) + `idx_certificate_pilgrimage` | 회수 뒤 재발행. 유니크를 먼저 지우면 외래키가 쓸 색인이 사라져 `ERROR 1553` 이 난다 — 평범한 색인을 먼저 깔고 지운다 |

`cert_serial` 표는 **신규**라 `CREATE TABLE IF NOT EXISTS` 가 그대로 만든다(ALTER 아님).

ALTER 후보였던 셋 중 둘은 **보강에서 실행**했다(`alter-ch7.sql` ⑦⑧):

1. ~~`reward_claim` 표~~ → **실행함**. 수령자·연락처·주소·메모
2. ~~`user_reward.status` 에 `APPROVED`·`SHIPPED`~~ → **상태를 늘리지 않기로** 했다. 대신 `tracking_no` 한 칸
3. `certificate.cert_type` 에 `INTERIM` — **제외 확정**(이 저장소의 중간본은 전자책이다)

---

## 5. 검증 숫자

```
JUnit    177 / 177        (챕터 7 신규 26건 — 완주 9 · 인증서 7 · 보상 10)
newman   본 실행 307요청 / 1399단언 · 회향 25요청 / 126단언 — 2회 연속 같은 수, 실패 0
런타임   ERROR 0 · 스택트레이스 0 · 5xx 0 · WARN 164 중 허용목록 밖 0
매퍼     매퍼↔XML 0(양방향) · #{}↔@Param 0 · 별칭 미채움 0  (select 75개)
보안     16항 통과 · 실패 0
SQL 채점 완주 0 · 유효 인증서 0 · 회수 3장 · 번호 중복 0 · 코스 원복 0
         보상 GRANTED 5 · PAID 1(needs_review 1) · REVOKED 1
```

SQL 채점의 마지막 줄이 이 챕터의 요약이다 — **회수된 실물 보상은 REVOKED 가 아니라 `needs_review` 로 남는다.**
W22 가 회향 뒤 완주를 다시 취소한 상태로 끝나기 때문에 유효 인증서는 0이고 회수본이 3장(코스 옛 번호·코스 새 번호·회향) 남는다.

### 폴더 W · W2

`W`(42요청) 는 본 실행에 있고, `W2`(25요청) 는 **별도 컬렉션 파일**(`postman/w2-hoehyang.postman_collection.json`)이다.
회향은 "ACTIVE 코스를 전부 완주" 라 준비 SQL 이 먼저 돌아야 하고, 그 SQL 은 newman 실행 중간에 끼울 수 없다.
`--folder` 로 고르는 방법도 있지만 폴더 이름이 한글이라 Git Bash 의 argv 에서 깨진다(정리.md §2).
`run-all.sh` 가 **본 실행 → 준비 SQL → 회향 실행 → 원복 → 채점 → 정리** 순으로 돈다.

| W | 무엇을 보나 | 결과 |
|---|---|---|
| W01·W01b | 4/5 는 완주가 아니다 | ✅ |
| W02·W03 | 마지막 승인 → 완주 | ✅ |
| W04 | `PG-yyyy-6자리` · VALID | ✅ |
| W05 | 공개 진위 확인에 개인정보 필드 없음(6종 확인) | ✅ |
| W06 | 없는 번호 404 `CERT-4041` | ✅ |
| W07 | 남의 인증서가 내 목록에 안 섞인다(1장 조회 API 부재라 목록으로) | ✅ |
| W08 | `claimable` = 실물 AND 신청 가능 상태 | ✅ |
| W09 | 다시 읽어도 그대로(재집계 API 부재 — 멱등만) | ✅ |
| W10 | 실물 아닌 보상 신청 → 400 `REWARD-4001` | ✅ |
| W15 | ACTIVE 1개면 회향 없음 (하한 12) | ✅ |
| W16·W17 | 회수 → 완주 취소 | ✅ |
| W17b·W17c | 인증서는 REVOKED 로 남고, 손대지 않은 보상만 회수 | ✅ |
| W18 | 회수된 번호도 200 + REVOKED | ✅ |
| W19·W19b | 재승인 → 새 번호, 옛 번호는 무효로 남음 | ✅ |
| W21·W21b | 회향 성립 → `HH-yyyy-6자리` + 실물 보상 claimable | ✅ |
| W11·W12 | 신청 접수 → 재신청 409 `REWARD-4092` | ✅ |
| W13 | 남의 보상 신청 차단(403 유지) | ✅ |
| W14 | 반려 사유 없이 → 400 | ✅ |
| W20·W20b | 승인 → 재승인 409 `REWARD-4090` | ✅ |
| W22·W22c | 회향 회수 · **지급된 실물은 REVOKED 가 아니다** | ✅ |
| W22d | 심사 목록에서 `needs_review` 가 맨 위 | ✅ |

### 실행 중에 잡힌 것 둘 (제품 결함은 아니지만 조용했다)

- **`cleanup.sql` 이 중간에서 멈추고 있었다.** mysql 은 첫 오류에서 끝나는데 러너가 stderr 를 버려
  아무도 몰랐다. 원인은 동시성 점검 계정(`conc-a`·`conc-b`)의 도장이 R23 이 넣는 점검용 원고를 참조해
  FK 1451 이 난 것 — 그 뒤 줄이 통째로 안 돌아 다음 실행이 하루 한도 429 로 무너졌다.
  정리 순서를 고치고, `run-all.sh` 가 정리 실패를 소리 내어 알리게 했다.
- **`mapper-check.js` ① 이 한 방향만 보고 있었다.** XML 에만 남은 문장을 일부러 심어 봤더니 못 잡았다.
  양방향으로 고쳤다(ch7-unused.md §0).

---

## 6. 기능 ↔ 엔드포인트 대조 (endpoints.md 가 정본 · 73개 유지)

| # | 교재 §6 의 기능 | 실제 | 상태 |
|---|---|---|---|
| 1 | 내 완주 목록 `GET /api/completions` | 없음 — 진행률은 `GET /api/courses/{id}` · `GET /api/passport` | **부재** |
| 2 | 관리자 재집계(1명) | `POST /api/admin/completions/recount/{userId}` (74) | ✅ **보강에서 신설** |
| 3 | 관리자 재집계(배치) | `POST /api/admin/completions/recount` (75) | ✅ **보강에서 신설** |
| 4 | 내 인증서 목록 | `GET /api/certificates` (29) | ✅ |
| 5 | 내 인증서 1장 `GET /api/certificates/{id}` | 없음 | **부재** |
| 6 | 공개 진위 확인 | `GET /api/certificates/verify/{serialNo}` (30) · permitAll | ✅ |
| 7 | 관리자 회수 | `POST /api/admin/certificates/{id}/revoke` (76) | ✅ **보강에서 신설** |
| 8 | 내 보상 목록 | `GET /api/rewards` (50) | ✅ |
| 9 | 수령 신청 | `POST /api/rewards/{userRewardId}/claim` (51) | ✅ |
| 10 | 관리자 심사 목록 | `GET /api/admin/rewards/claims/pending` (11) — `?status=` 아님 | ✅(경로 다름) |
| 11 | 관리자 승인/발송/반려 | `POST /api/admin/rewards/claims/{id}/review` (10) — 승인·반려 한 곳, 발송 없음 | ✅(발송 부재) |

챕터 7 본편에서는 엔드포인트를 하나도 만들지 않았고(73개 유지), **보강에서 허용된 3개만** 더해 76개가 됐다.
남은 부재 3건(내 완주 목록·인증서 1장 조회·발송 단계)은 여전히 [질문]이다.

---

## 7. [질문]

| # | 무엇 | 지금 상태 | 정하면 |
|---|---|---|---|
| 1 | ~~**재집계 엔드포인트**~~ | **보강에서 닫힘** — 1명·배치 둘을 신설했다. 커밋 뒤 리스너가 실패해도 이제 손으로 맞출 수 있다 | 남은 것은 "내 완주 목록"(사용자용) 하나 |
| 2 | ~~**관리자 인증서 회수**~~ | **보강에서 닫힘** — 엔드포인트 신설로 회수 사유 `ADMIN` 과 `CERT-4091`(재회수 409)이 살아났다 | — |
| 3 | ~~**배송 정보**~~ | **보강에서 닫힘** — `reward_claim` 표 신설, claim 본문 4필드, 목록 응답에는 미포함 | — |
| 4 | **중간본(INTERIM) 인증서** | **보강에서 "제외" 확정** — 이 저장소의 중간본은 전자책이다. 코드·W 채점에서 뺐다 | 인증서로도 낼 것인지는 콘텐츠 결정. 낸다면 `cert_type` ALTER |
| 5 | ~~**발송(SHIPPED) 단계**~~ | **보강에서 닫힘** — 상태를 늘리지 않고 승인(PAID) 전이 때 송장 문자열(0~100자)을 함께 받는다 | — |
| 6 | **회원 탈퇴** | 기능 자체가 없다(`withdraw` 는 약관 철회다). 회수 사유 `USER_WITHDRAWN` 도 쓸 자리가 없다 | 챕터 1 범위 |
| 7 | ~~**회수된 보상이 되살아나지 않는다**~~ | **보강에서 닫힘** — `grantOrRestore()` 가 같은 행을 `GRANTED` 로 되돌린다(새로 적립하지 않는다). W27 이 실측 |
| 8 | **도장 단위 보상은 그 도장이 반려돼도 회수되지 않는다** | §2-4 는 "그 완주로 적립된 보상" 만 말한다. 도장 보상은 `stamp_id` 로 매여 있어 완주 취소의 대상이 아니다 | 반려된 도장의 보상도 회수할지 |
| 9 | **`CERT-4041` vs 교재의 `CERT-4040`** | 저장소에 `CERT-4040` 은 **존재한 적이 없다** — 404 코드는 `CERT-4041` 하나뿐이라 지울 것이 없었다 | 권고와 근거는 `ch7-hardening.md` §6(결정은 명세 §7 확인 뒤) |

---

newman 332/332, 기능 ✅ 21 · ⚠ 0 · ❌ 0 · — 6
