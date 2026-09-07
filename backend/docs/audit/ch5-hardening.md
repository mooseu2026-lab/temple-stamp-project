# 챕터 5 보강 — 정밀 점검 A·B 반영

원문 `제시.md` 3부 · 루트 `src/` · git 없음 · 챕터 6 미착수
시작 상태: JUnit 127 · newman 요청 132 / 단언 578 · 실패 0

---

## 0. 무엇을 고쳤나

| # | 항목 | 상태 |
|---|---|---|
| **A-1** | 코스 하나만 끝내도 회향이 발급되던 것 — **하한 12** | ✅ |
| **A-2** | `StampController` 의 `@Positive` 가 통째로 무시되던 것 | ✅ |
| **B-1** | `PhraseService` 가 `readOnly = true` 인데 쓰던 것 | ✅ |
| **B-2** | 반려 → 재승인 시 중간본·회향본이 두 번 쌓이던 것 | ✅ |
| **B-3** | 어제 만료된 행을 재사용하면 오늘 한도에서 빠지던 것 | ✅ |
| **B-5** | 증빙이 `EXPIRED` 를 거쳐 접수되던 것 | ✅ |
| B-4 | MySQL `time_zone` — 코드가 아니라 배포 환경 | 정리.md §5-2 9번 (변경 없음) |

---

## 1. A-1 — 회향 하한 (1순위)

### 무엇이 문제였나

`CompletionService` 는 회향을 `completedCourses >= courseService.countActiveCourses()` 로 판정했다.
데이터가 다 찼을 때는 옳지만 **코스가 적을 때 너무 쉽게 참이 된다.**
지금 ACTIVE 코스는 데모 1개뿐이라(시드 12코스는 전부 DRAFT) `1 >= 1` 이 되어,
코스 하나를 끝낸 사람에게 회향까지 한꺼번에 나갔다. 실측 결과였다:

```
rewards: 순례 도장 · 코스 완주 쿠폰 · 회향 기념품(PHYSICAL, claimable:true)
cert:    PILGRIMAGE + HOEHYANG
ebook:   PILGRIMAGE(QUEUED) + HOEHYANG(QUEUED)
```

**"회향 기념품" 은 신청 가능한 실물 보상**이다. 인증서 번호가 이미 제3자에게 제시됐을 수 있어
회수도 지저분하다 — 문을 열기 전에 막아야 하는 종류였다.

### 어떻게 고쳤나

```java
int totalCourses = courseService.countActiveCourses();
if (totalCourses >= rewardProperties.hoehyangMinCourses() && completedCourses >= totalCourses) {
```

| 자리 | 값 |
|---|---|
| `RewardProperties.hoehyangMinCourses` | 새 필드 |
| `application.yml` | `reward.hoehyang-min-courses: ${HOEHYANG_MIN_COURSES:12}` |
| `.env` | `HOEHYANG_MIN_COURSES=12` |

상수로 박지 않고 설정값으로 뺀 이유는 챕터 4 마감 때와 같다 — 운영에서 조정할 길을 남긴다.
**하한을 못 넘기면 회향이 안 나가는 쪽으로 기운다.** 데이터가 덜 찼을 때 안 주는 것은 나중에 줄 수 있지만,
잘못 준 것은 되돌리기 어렵다.

### 테스트 2건 (`CompletionHardeningIntegrationTest`)

| 테스트 | 확인 |
|---|---|
| 하한 미달 | ACTIVE 코스가 12 미만일 때 5칸을 다 채워도 → 코스 완주 인증서는 나오고 **회향 인증서 0 · 회향본 0 · 회향 보상 0** |
| 하한 충족 | 시드 코스를 잠시 ACTIVE 로 올려 12개 이상을 만들고 전부 완주 → **회향 인증서 1 · 회향본 1** |

두 번째는 코스를 실제로 12개 열 수 없어(사찰 110곳의 QR 힌트가 없다) `status` 만 잠시 바꾼다.
`tearDown` 이 `DRAFT` 로 되돌리므로 검증 세트에 영향이 없다 — `run-all.sh` 2회 연속 같은 값으로 확인했다.

---

## 2. A-2 — `@Validated` 와 경로 변수 제약

`StampController` 에 **`@Validated` 가 없었다.** 클래스에 그것이 없으면 파라미터 제약은
예외도 경고도 없이 통과한다(정리.md §6-6). 게다가 `@Positive` 가 붙어 있던 것도 `by-slot` 하나뿐이었다.

```
전  GET /api/stamps/-5  → 404 STAMP-4040     (형제 컨트롤러는 400 COMMON-4000)
후  GET /api/stamps/-5  → 400 COMMON-4000
   GET /api/stamps/by-slot/0 → 400 COMMON-4000
```

`@Validated` + 경로 변수 6곳에 `@Positive`. 컬렉션에는 스탬프 경로의 404 단언이 없어 수정할 것이 없었고,
테스트 1건(`A-2 경로 변수의 @Positive 가 실제로 걸린다`)을 새로 넣었다.

곁들여 `bySlot` 의 주석에서 틀린 설명을 고쳤다 — "문자열이 들어오면 `@Positive` 때문에 형식 오류" 라고
적어 두었는데, 문자열은 제약과 무관하게 **타입 변환**에서 막힌다.

---

## 3. B-1 — `readOnly = true` 인데 쓰는 메서드

```java
@Transactional(readOnly = true)      // 클래스 — 유지
public class PhraseService {
    @Transactional public Mission pickMission(...)      // task_seen 에 쓴다
    @Transactional public void markPhraseSeen(...)      // phrase_seen 에 쓴다
```

지금은 둘 다 `StampService` 의 쓰기 트랜잭션 안에서 불려 **바깥에 합류**하므로 통과하고 있었다.
읽기 전용 트랜잭션에서 부르거나 트랜잭션 없이 부르는 **호출자가 하나만 생기면** 그 순간
`Connection is read-only` 로 500 이다. 클래스의 `readOnly` 는 그대로 두고 두 메서드에서만 되돌렸다.

`validatePhraseFor` 는 조회만 하므로 붙이지 않았다(지시문에 있었으나 쓰기가 없어 불필요하다).
`markTaskSeen` 이라는 이름의 메서드는 없다 — 그 기록은 `pickMission` 안에서 일어난다.

---

## 4. B-2 — 전자책 대기열 멱등

인증서 발급(`findByPilgrimageId().orElseGet(...)`)과 보상 적립(`grant()` 가 0행이면 건너뜀)은
이미 멱등한데 **전자책만 그 보호가 없었다.** 코스본은 `(pilgrimage_id, type)` 으로 막히지만
**중간본·회향본은 `pilgrimage_id` 가 NULL** 이라 그 검사를 통과해 버린다.

```xml
<select id="findAliveByUserAndType">
    ... WHERE user_id = #{userId} AND ebook_type = #{ebookType}
          AND pilgrimage_id IS NULL
          AND status IN ('QUEUED', 'BUILDING', 'READY')
```

`FAILED` 만 "없는 것" 으로 본다 — 만들다 실패한 것은 다시 큐에 넣을 수 있어야 한다.

**테스트**: 회향까지 발급된 상태에서 마지막 도장을 반려 → 다시 PENDING → 승인(완주 연쇄를 한 번 더 태움)
→ `ebook` 행 수 불변 · 회향본 1건 유지.

---

## 5. B-3 — 만료 행 재사용 시 `created_at` 갱신

하루 한도는 `created_at >= CURDATE()` 기준인데, `findOrCreate` 는 만료·반려된 행을 **재사용**한다.
그때 `created_at` 은 어제 그대로라 그 도장이 오늘 한도에 잡히지 않았다.

```sql
UPDATE stamp
SET verify_status = 'GPS_DONE', verify_method = 'GPS_QR', site_id = #{siteId},
    created_at = NOW(),          -- 다시 시작하는 것이므로 시각도 다시 건다
    accuracy_grade = ..., gps_verified_at = NOW(), ...
```

**`SET` 순서 확인** — 이 절의 대입은 전부 상수·파라미터이고 **다른 컬럼을 읽는 것이 하나도 없다.**
그래서 왼쪽부터 평가되는 MySQL 규칙(정리.md §6-1)에 걸리지 않는다. XML 주석에 그 근거를 적어 두었다.

**테스트**: 어제 날짜로 `EXPIRED` 행을 하나 심고 → 오늘 한도 0 확인 → 같은 자리 gps-check →
한도 1로 잡히고 **행 수는 그대로**(새로 만든 게 아니라 재사용).

---

## 6. B-5 — 증빙은 처음부터 `PENDING`

`findOrCreateForEvidence` 는 행을 `EXPIRED` 로 만들어 두고(그 상태라야 `markPending` 이 받아 준다)
그다음 `markPending` 을 쳤다. 같은 트랜잭션이라 실패하면 함께 롤백되지만,
**"시작 상태로 EXPIRED 를 쓴다" 는 것 자체가 읽는 사람을 속인다** — 만료된 적이 없는데 만료로 보인다.

교재 [기본 45] 의 `insertEvidence` 대로 처음부터 `PENDING` 으로 넣는다.

```xml
<insert id="insertEvidence" ...>
    INSERT INTO stamp (pilgrimage_id, course_site_id, site_id, verify_status, verify_method,
                       pending_reason, user_sentence, evidence_photo_key)
    VALUES (..., 'PENDING', 'EVIDENCE', 'EVIDENCE', ...)
```

`findOrCreateForEvidence` 는 지우고 `insertOrReuseEvidence` 로 바꿨다 — 그 자리에 진행 중이던 행이
있으면 그것을 `PENDING` 으로 넘기고(기존 동작), 없으면 새로 넣는다.

**테스트**: 접수 직후 행이 `PENDING` · `EVIDENCE` · `pending_reason=EVIDENCE` · `evidence_photo_key` 존재 ·
`site_id` 채워짐.

---

## 7. 검증

```
JUnit   133/133   (127 → +6)
newman  요청 132/132 · 단언 578/578 · 실패 0   ← 2회 연속 같은 값(멱등)
SQL     채점 9절 전부 기대값
```

| 새 테스트 | 건수 |
|---|---:|
| `CompletionHardeningIntegrationTest` (신규) | 3 (회향 하한 미달 · 하한 충족 · 전자책 멱등) |
| `StampFlowIntegrationTest` (추가) | 3 (경로 변수 검증 · 만료 행 재사용 한도 · 증빙 PENDING) |

### 정밀 점검 3부의 방법 1~4 재확인

| 방법 | 결과 |
|---|---|
| ① 매퍼 인터페이스 ↔ XML 문장 id 전수 대조 | **불일치 0건** |
| ② XML `#{param}` ↔ `@Param` 전수 대조 | **불일치 0건** (`foreach` item 별칭 1건은 오탐) |
| ③ `resultType` 필드 ↔ SELECT 별칭 (**71개** select) | **미채움 0건** |
| ④ 실기동 후 30개 경로 호출 | **5xx 0건** |

④ 에서 A-2 도 함께 확인했다 — `/api/stamps/-5`·`/api/stamps/by-slot/0` 둘 다 400 `COMMON-4000`.

---

## 8. 남은 것

| # | 내용 | 왜 지금 안 하나 |
|---|---|---|
| C-1 | 시드 사찰 110곳·코스 12개가 DRAFT — `qr_location_hint` 필요 | 콘텐츠(현장 답사)가 있어야 한다 |
| C-2 | 좌표 `(0,0)` 인 사찰 18곳 | 〃 |
| C-5 | 사진 1장·문장 1개 개인 소장 (`PUT /api/photos/{siteId}`) | **챕터 6** |
| C-6 | 보상·인증서·전자책·생각상자·명상의 종단 테스트 | **챕터 6~9** |
| B-4 | MySQL `time_zone` 이 `SYSTEM` | 코드가 아니라 배포 환경 — 정리.md §5-2 9번 |

**A-1 을 막았다고 해서 C-1·C-2 가 덜 급해진 것은 아니다.** 지금도 사용자에게 열린 코스는 데모 1개뿐이고,
시드 12코스를 열려면 사찰 110곳의 QR 힌트와 좌표 18곳이 먼저 채워져야 한다.

---

**newman 578/578, 기능 ✅ 6 · ⚠ 0 · ❌ 0 · — 5**

- ✅ 회향 하한 · 경로 변수 검증 · `readOnly` 되돌림 · 전자책 멱등 · 만료 행 한도 · 증빙 PENDING 직행
- ⚠ 없음
- — 콘텐츠 대기 3건(QR 힌트 · 좌표 18곳 · 챕터 6 사진) · 종단 테스트 없는 도메인(챕터 6~9) · 배포 시 MySQL `time_zone` 확인
