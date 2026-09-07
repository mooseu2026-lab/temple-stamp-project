# 챕터 11 — 리뷰 반영 · 사은품 정책 확정 · 이동시간 검사 활성화

정본 `backend/docs/textbook/ch11.md` · 리뷰 원문 `audit/review-2026-09-07.md` · 2026-09-07
엔드포인트 신설 0(91 불변) · 교체 원본 `audit/ch11-replaced/`

---

## 0. 한눈에

| | |
|---|---|
| 항목 | **14 / 14** |
| 확인포인트 Y | **15 / 15**(newman 23요청·단언 102 · JUnit · 스크립트) |
| JUnit | **305 / 305**(279 → 305, +26) |
| 엔드포인트 | **91**(불변) |
| H17 ALTER 사슬 ↔ schema.sql | 차이 **0** (사슬에 alter-ch11 추가) |
| all.sh | **초록 6/6** (7분 53초) — newman 1·2회차 통과 · 2회 바이트 동일 · DB H1~H18 어긋남 0 · 보안 86/0 · 교착 0 · 5xx 0 |
| 감사 H 매칭율 | 92% → **93%**(INTERIM ⛔ → ✅) |
| ALTER | ⑳ `user_reward` · ㉑ `slot_site` · ㉒ `ebook` + `reward_policy` UPDATE |

---

## 1. 항목 14건 판정

### 2-1 리뷰 코드 결함 3건

| # | 항목 | 판정 | 무엇을 했나 |
|---|---|---|---|
| 1 | `findLastCompletedSiteId` 가 대표 사찰을 준다 | ✅ | `SELECT COALESCE(st.site_id, cs.site_id)` — 실제로 인증한 후보를 준다. 옛 행은 `st.site_id` 가 비어 있어 COALESCE 로 받는다 |
| 2 | CORS 에 `X-Request-Id` 가 없다 | ✅ | `setAllowedHeaders` 에 `RequestIdFilter.HEADER` · `setExposedHeaders` · `setMaxAge(3600)` · `RequiredEnvCheck.PROD_REQUIRED` 에 `FRONTEND_URL` |
| 3 | 사용자 단위 보상에 사용자 단위 유니크가 없다 | ✅ | ALTER ⑳ + `RewardPolicy.isUserScoped()` + `grantOrRestore` 에서 코스 id 를 비우고 마일스톤을 담는다 |

**1 의 요점** — 자리의 대표 사찰과 실제로 도장을 찍은 후보 사찰이 다를 수 있다(v4). 대표를 돌려주면 이동시간이 **엉뚱한 두 점 사이**로 계산된다. 후보 사찰이 대표보다 멀면 검사를 그냥 통과하고, 가까우면 정상 이동을 보류로 잡는다. 둘 다 조용한 오답이다.

**3 의 요점** — 지금까지 보상의 멱등 키는 `(정책, 도장)`·`(정책, 순례)` 둘뿐이었다. "이 사람의 누적" 에 주는 보상(3코스마다의 전자일기장, 12코스의 특별앨범)도 **방금 끝낸 코스의 id** 로 저장돼서, 코스 하나를 반려했다가 재승인하면 다른 코스 id 로 **한 행이 더** 생긴다. 실물 기념품이라면 두 개가 나간다.
`ACTIVE` 코스가 1개인 지금은 이 길에 도달할 수 없다. 그래도 12코스를 열기 전에 반드시 닫아야 하는 문이라 지금 닫았다.

> **MySQL 유니크는 NULL 을 서로 다른 값으로 본다**(§6-19). `pilgrimage_id` 를 NULL 로 두는 것만으로는 막히지 않아서, NULL 을 접는 생성 컬럼이 필요하다 — `user_key = CASE WHEN stamp_id IS NULL AND pilgrimage_id IS NULL THEN user_id END`.

### 2-2 사은품 정책 (결정 A·B·C)

| # | 항목 | 판정 | 무엇을 했나 |
|---|---|---|---|
| 4 | `reward_policy` 확정 | ✅ | `RW-STAMP`·`RW-COURSE` → `is_active=0` · `RW-INTERIM` → 전자일기장 / `DIGITAL` / `EVERY_THREE_COURSES` · `RW-HOEHYANG` → 맞춤형 특별앨범 / `PHYSICAL`. `data.sql`·`schema.sql`·`alter-ch11.sql` 셋 다 |
| 5 | 3코스**마다** 적립 | ✅ | `completedCourses >= 3 && completedCourses % 3 == 0` · `milestone=completedCourses` · 전자책 큐도 같은 자리에서 |
| 6 | `EbookService` INTERIM 재도입 | ✅ | 재료 = 사용자 전체 기록(도장 무관) · `(user_id, ebook_type, milestone)` 멱등 · `POST /api/ebooks?type=INTERIM` |
| 7 | 취소 연쇄 | ✅ | 전자일기장 보상·전자책은 회수하지 않는다 — **구조로** 걸리지 않는다(아래) |
| 8 | 보상 목록 `legacy` | ✅ | `(rp.is_active = 0) AS legacy` · `claimable` 에 `&& !legacy` · **`claim()` 에도 같은 검사** |

**5 의 요점(리뷰 2-3)** — 등호(`== 3`)로 두면 이미 4코스를 완주한 사람에게 3 마일스톤 행이 없을 때 **다시는 못 받는다**. `>=` 와 `%` 와 마일스톤 유니크가 **함께** 있어야 다음 완주가 빠진 것을 스스로 채운다(자기 치유).
같은 이유로 `completeCourse` 에 남아 있던 옛 `if (completedCourses == 3) enqueue(INTERIM)` 을 **지웠다**. 마일스톤 없이 한 번 넣으면 `(사용자, INTERIM, 0)` 행이 먼저 자리를 잡아 3·6·9·12 가 영영 만들어지지 않는다.

**6 의 요점** — "재료가 없다" 의 기준이 **도장 수에서 전체 기록으로** 바뀌었다. 전에는 `m.stampCount() == 0` 이면 400 이라, 절에 가서 **사진만 찍고 생각상자만 쓴 사람**은 만들 재료가 있는데도 거절당했다. 전자일기장은 도장을 요구하지 않는 책이므로 이 기준이 틀렸다.
그래서 재료에 **도장에 붙지 않은 사진**을 새로 넣었다(`findLoosePhotos`). 사진은 지금까지 도장 질의에 조인돼서만 책에 들어왔다. 같은 사찰에 완료 도장이 있으면 그 사진은 도장 페이지에 이미 실리므로 `NOT EXISTS` 로 뺀다 — 두 번 나오면 안 된다. 타인 얼굴 사진은 여기서도 제외, 비공개는 그대로 싣는다(개인 소장본).

**7 의 요점** — 코드를 더하지 않았다. 두 정책이 모두 사용자 단위라 `pilgrimage_id` 가 NULL 이고, `revokeOrFlagForPilgrimage(pilgrimageId)` 의 그물에 애초에 걸리지 않는다. 회수 대상은 `revokeOrFlagByTrigger(userId, ALL_COMPLETED)` 의 특별앨범뿐이다. **구조가 결정을 이미 지키고 있어서** 확인과 주석·테스트만 더했다(`RewardServiceTest.diary_survives_cancellation`).

**8 의 요점** — `claimable` 은 한 곳에서만 계산한다는 챕터 7 §4-3 규칙 때문에, `legacy` 검사를 목록에만 넣으면 **버튼은 꺼져 있는데 눌러 보면 통과하는** 상태가 된다. 그것이 전체 점검 C 의 결함 5-1 이었다. `claim()` 에도 같은 검사를 넣어 `REWARD-4001` 로 막는다.

### 2-3 이동시간 검사 활성화 (결정 D·E)

| # | 항목 | 판정 | 무엇을 했나 |
|---|---|---|---|
| 9 | `default-travel-minutes: 15` | ✅ | `application.yml` + **`application-prod.yml` 에도 명시** · "0이면 검사 꺼짐 — 운영 금지" 주석 |
| 10 | 사용자 단위로 | ✅ | 두 질의 다 `pilgrimage_id IN (그 사용자의 순례)` · `travelTimeShortfall(userId, siteId)` |
| 11 | `site-distance-sample.csv` | ✅ | ACTIVE 코스 20행을 채운 본보기 + **관리자 API 가 이름→id 변환을 하지 않는다는 확인** |
| 12 | `정리.md` §7 에 240행 | ✅ | §7-7 콘텐츠 트랙에 추가(인계문 §6-7 누락 지적 반영) |

**9 를 prod 에도 적은 이유** — 상속만으로 충분하지만, `application.yml` 의 값을 0으로 되돌리는 변경이 운영까지 그대로 가는 길을 막는다. actuator 노출을 두 곳에 적어 둔 것과 같은 이유다.

**10 의 요점** — 코스 단위로 보면 A코스 마지막 도장을 찍은 5분 뒤 B코스 첫 도장을 찍는 순간이동이 **"이 코스의 첫 도장"** 으로 보여 검사 없이 통과한다. 두 질의(**어느 절에서** / **몇 분 전에**)의 범위가 어긋나면 서로 다른 도장을 가리키게 되므로 둘을 같이 바꿨다.

**11 의 확인 결과 — `PUT /api/admin/site-distances` 는 이름을 id 로 바꾸지 않는다.** `{siteAId, siteBId, minMinutes}` 만 받는다(한 번에 최대 240행). 그래서 양식에 `site_a_id`·`site_b_id` 칸을 두고, 이름으로 id 를 찾는 질의를 머리말에 적었다. 이름은 사람이 표를 읽기 위한 것이고 서버로 가는 것은 id 다.
표에 조회 API 가 없는 것도 일부러다 — 최소 이동시간이 응답에 실리면 "그만큼 기다렸다 찍으면 된다" 가 되어 심사가 통째로 우회된다.

### 2-4 정확도 (결정 F)

| # | 항목 | 판정 | 무엇을 했나 |
|---|---|---|---|
| 13 | `AccuracyGrade` HIGH ≤30m · MID ≤100m · LOW | ✅ | javadoc 정정 + `frontend-handoff.md` §정확도 + `frontend-design.md` §5 S-06 표 |

**서버는 좌표를 받지 않으므로 미터를 다시 잴 수 없다.** 경계는 프론트(`location.js`)가 `coords.accuracy` 를 접는 기준이자 이 이름들의 뜻이다. 두 곳이 어긋나면 같은 상황이 기기마다 다른 등급으로 올라오므로, 주석과 인계문을 **한 쌍**으로 못 박고 "바꿀 때는 둘을 함께" 를 양쪽에 적었다. 현장 실측 전 값이다.

### 2-5 slot_site 유니크 (결정 H)

| # | 항목 | 판정 | 무엇을 했나 |
|---|---|---|---|
| 14 | `UNIQUE (site_id, track)` + 409 `COURSE-4093` + 시더 멱등 | ✅ | 사전 검사 0행 확인 후 ALTER ㉑ · `SlotSiteService.assign` 신설 · 시더가 그 문을 지난다 |

**사전 검사** `SELECT site_id, track, COUNT(*) FROM slot_site GROUP BY site_id, track HAVING COUNT(*) > 1` → **0행**(218행 중). 그 뒤 ALTER 성공.

**DB 유니크만으로는 부족했다.** `SlotSiteMapper.upsert` 는 `ON DUPLICATE KEY UPDATE` 라, 다른 자리가 이미 그 (사찰, 트랙) 을 가지고 있으면 예외가 아니라 **남의 행을 조용히 고치고 성공을 돌려준다.** 부른 쪽은 후보를 붙였다고 믿는데 행은 여전히 앞 자리의 것이다 — 새 유니크가 만들어 낸 새 함정이다. 그래서 쓰기를 `SlotSiteService.assign` 한 곳으로 모아 주인을 먼저 확인하고 409 를 낸다. 시더도 그 문을 지난다(215행 재실행 멱등).

---

## 2. 확인포인트 Y — 15/15

무엇으로 확인했는지를 함께 적는다. 셋 다 같은 무게가 아니다 — newman 은 **HTTP 계약**을, JUnit 은 **DB 와 서비스 규칙**을, 스크립트는 **환경과 개수**를 본다.

| Y | 확인 | 결과 | 근거 |
|---|---|---|---|
| Y01 | 후보 사찰(대표 아님)에서 도장 → 다음 자리 5분 내 | ✅ 실제 인증 사찰 기준 | JUnit `TravelTimeTest.last_site_is_the_one_actually_verified` · `old_rows_fall_back_to_the_representative` |
| Y02 | 코스 A 완료 직후 코스 B 첫 자리 5분 내 | ✅ PENDING(사용자 단위) | JUnit `scope_is_the_user_not_the_course` · `unknown_pair_uses_the_default` |
| Y03 | 사이 거리 없는 쌍 | ✅ 기본 15분 적용 | **newman Y03** (자리1 완료 직후 자리2 → `PENDING`) · JUnit `default_is_fifteen_minutes` |
| Y04 | 사이 거리 표 UPSERT 후 | ✅ 표 값 우선 | JUnit `the_table_wins_over_the_default` |
| Y05 | 도장 1개 완료 | ✅ 보상 0 | **newman Y05** (`rewards` 빈 배열) · `Y05-d` 보상 목록 0건 |
| Y06 | 3코스 완주 | ✅ 보상 1 + INTERIM 큐 1, `milestone=3` | JUnit `CompletionServiceTest.diary_every_three_courses` |
| Y07 | 그중 1코스 반려 → 재승인 | ✅ 행 수 불변 | JUnit `RewardUserScopeTest.revoke_then_regrant_with_another_course_keeps_one_row` |
| Y08 | 6코스 | ✅ `milestone=6` 새 행, 3은 그대로 | JUnit `diary_every_three_courses` · `diary_is_one_row_per_milestone` |
| Y09 | 12코스 | ✅ 특별앨범 1 + HH 인증서 + 회향본 | JUnit `CompletionServiceTest.hoehyang_when_all_active_courses_are_done`(기존) |
| Y10 | 도장 없는 사용자가 `POST /api/ebooks` | ✅ 생성 성공 · 기록 0이면 400 | **newman Y10·Y10-c·Y10-d·Y10-e** · JUnit `EbookInterimTest` 6건 |
| Y11 | 억지 INSERT 중복 | ✅ 1062 | JUnit `duplicate_insert_is_rejected_by_the_unique_key` · `SlotSiteUniqueTest.the_database_is_the_last_line` |
| Y12 | OPTIONS preflight | ✅ 3항목 + 거부 오리진 | **newman Y12·Y12-b** · JUnit `CorsTest` 2건 |
| Y13 | prod 드라이런 `FRONTEND_URL` 없음 | ✅ 기동 실패 | `RequiredEnvCheck.PROD_REQUIRED` (챕터 11 STEP 0 에서 실측) |
| Y14 | slot_site 중복 후보 등록 | ✅ 409 `COURSE-4093` | JUnit `SlotSiteUniqueTest.the_same_site_cannot_join_another_slot_on_the_same_track` |
| Y15 | 엔드포인트 91 · H17 차이 0 | ✅ 91 · 0 | `endpoint-scan.js` · `db-check.sh` H17 |

**newman `Y` 폴더** — 23요청 · 단언 **102** · 실패 **0**.
`cleanup.sql` 에 `y-%@test.com` 을 11곳 더해 두 번 돌려도 같은 결과가 나온다.

**전역 규약 검사를 하나 고쳤다.** 컬렉션의 전역 test 가 "본문이 있으면 JSON 봉투" 를 단언했는데, CORS 거부는 **필터 앞단이 내는 평문**이라 봉투가 없다(그리고 없는 것이 맞다). `Content-Type` 이 `application/json` 일 때만 봉투를 보도록 좁혔다 — `X-Request-Id` 검사는 그대로 모든 응답에 돈다.

---

## 3. JUnit — 279 → 305 (+26)

| 클래스 | 건수 | 무엇을 고정하나 |
|---|---|---|
| `RewardUserScopeTest` (신설) | 4 | 사용자 단위 보상에 코스 id 가 없다 · 취소→다른 코스 재승인에도 한 행 · 마일스톤마다 한 행 · 억지 INSERT 는 1062 |
| `TravelTimeTest` (신설) | 6 | 기본 15분 · 실제 인증 사찰 · COALESCE 되돌림 · 사용자 단위 · 표 우선 |
| `EbookInterimTest` (신설) | 6 | 기록 0 → 400 · 사진·생각상자만으로 생성 · 타인 얼굴 제외 · 3코스 미만은 PERSONAL · 4코스는 마일스톤 3 · 멱등과 다음 마일스톤 |
| `CorsTest` (신설) | 2 | preflight 3항목 · 거부 오리진 |
| `SlotSiteUniqueTest` (신설) | 5 | 같은 자리 멱등 · 다른 자리 409 · 다른 트랙 허용 · DB 1062 · 현행 데이터 위반 0 |
| `RewardServiceTest` (보강) | +2 | 전자일기장은 취소에 걸리지 않는다 · `legacy` 는 표시만 되고 신청은 막힌다 |
| `CompletionServiceTest` (보강) | +1 | 3·4·6 코스에서 마일스톤이 어떻게 늘어나는가 |

**기존 테스트 4건의 기대값을 바꿨다.** 정책을 껐으므로 "도장 1개에 보상 1건 이상"·"코스 완주 보상 1" 은 이제 **0** 이 맞다. 통과시키려고 고친 것이 아니라 **정책이 바뀌어 기대값이 바뀐** 것이라, 바뀐 이유를 주석으로 남겼다.

- `StampFlowIntegrationTest.T-01` — `rewards` 가 빈 배열
- `MissionConcurrencyIntegrationTest` — `courseRewards` 0
- `RewardServiceTest.digitalReward()` — 코스 쿠폰 대신 전자일기장으로
- `CompletionServiceTest.revoke_only_untouched_rewards` → `single_course_grants_no_reward` 로 대체(회수 규칙은 `RewardServiceTest` 가 본다)

---

## 4. 만들면서 걸린 것 — 세 가지

### ① ERROR 3819 ×2 — CHECK 제약의 순서가 서로를 막는다

`reward_type` 에 `DIGITAL` 이 없어서 `UPDATE` 가 막혔고, `trigger_type` 을 `EVERY_THREE_COURSES` 로 바꿀 때는 **옛 CHECK 가 새 값을 거부하고 새 CHECK 가 옛 값을 거부**했다. 지나갈 수 있는 순서는 하나뿐이다 — `DROP CHECK` → `UPDATE` → `ADD CONSTRAINT`.

> **한 번 실패한 뒤 다시 돌릴 때.** 실패한 실행이 `DROP CHECK` 까지는 이미 해 놓았을 수 있다. 그 상태에서 파일을 통째로 다시 돌리면 두 번째 `DROP` 이 **ERROR 3821**(없는 제약)로 멈춘다. 남은 `UPDATE` + `ADD` 만 골라 돌린다. 실제로 그렇게 복구했다.

### ② 생성 컬럼이 개인 소장본을 사용자당 한 권으로 묶었다 — 이 챕터가 만든 결함

처음에는 `ebook.milestone_key` 를 `user_reward` 와 똑같이 `IFNULL(milestone, 0)` 으로 썼다. 그러자 마일스톤이 없는 **모든** 책이 `0` 으로 접히면서 `UNIQUE (user_id, ebook_type, milestone_key)` 가 **개인 소장본을 사용자당 한 권**으로 묶었다. `PrintOrderServiceTest` 가 `Duplicate entry '38270-PERSONAL-0'` 로 터져서 잡았다.

```sql
-- 고친 뒤: 전자일기장에만 값이 있다. 나머지는 NULL 이고, MySQL 은 NULL 을 서로 다른 값으로 보므로
--          제약 자체가 걸리지 않는다.
milestone_key SMALLINT GENERATED ALWAYS AS
    (CASE WHEN ebook_type = 'INTERIM' THEN IFNULL(milestone, 0) END) STORED
```

같은 수법(§6-19)이 두 표에서 **정반대 방향**으로 쓰인다. `user_reward` 는 NULL 을 접어 **제약을 걸려고**, `ebook` 은 NULL 을 남겨 **제약을 걸지 않으려고** 쓴다. 같은 도구라도 "무엇을 유일하게 만들 것인가" 를 먼저 정하지 않으면 반대로 동작한다.

### ③ `ON DUPLICATE KEY UPDATE` 는 새 유니크를 만나면 남의 행을 고친다

②와 같은 종류다. 유니크를 하나 더 걸면 그 유니크에 걸리는 UPSERT 는 **의도하지 않은 행**을 갱신 대상으로 삼는다. 항목 14 의 `SlotSiteService` 가 그것을 막는 자리다. 유니크를 새로 걸 때는 **그 표를 UPSERT 하는 문장을 전부 다시 읽어야 한다.**

---

## 5. 문서 갱신

| 문서 | 무엇을 |
|---|---|
| `정리.md` §3 | **원칙 ⑩** — 이동시간은 사용자 단위, 기본값 0 금지 |
| `정리.md` §4-19 (신설) | 사은품 두 종 · `legacy` · `POST /api/ebooks?type=INTERIM` 4갈래 표 · 정확도 경계 표 · CORS |
| `정리.md` §5-1 덧 3 (신설) | ALTER ⑳㉑㉒ 전문 · CHECK 순서 · 3821 복구 · `SlotSiteService` 짝 |
| `정리.md` §7-7 | 콘텐츠 트랙에 **사이 거리 240행** |
| `정리.md` §7-11 (신설) | 이 챕터가 남긴 것 5건 |
| `audit/frontend-handoff.md` | 정확도 경계 · 이동시간 검사가 켜졌다 · 보상 `legacy` |
| `frontend/frontend-design.md` §5 | S-06 정확도 표 · S-13 전자일기장 |
| `frontend/api-types.d.ts` | 재생성 — `EbookMaterials.photos` · `EbookResponse.milestone` · `RewardResponse.legacy` |
| `verify/db-check.sh` | H17 사슬에 `alter-ch11` 추가 |
| `verify/cleanup.sql` | `y-%@test.com` 11곳 |
| `verify/security-check.sh` | S12 의 **이름이 검사 대상과 달랐다** — `ThinkboxMapper` 의 <b>공개 목록</b> 질의를 보면서 "전자책 수록 질의가 is_private = 0 만 고른다" 라고 적혀 있었다. 챕터 9 결정은 전자책에 비공개도 싣는 것이라, 이름을 두면 **틀린 사실이 매 실행 초록으로 찍힌다**(§6-11). 이름을 사실에 맞추고 전자책 재료의 `has_other_face` 필터 검사를 하나 더했다 — 보안 85 → **86** |

---

## 6. 감사 H 재집계

| | 이전(챕터 10) | 지금 |
|---|---|---|
| 매칭율 | 92% | **93%** |
| ✅ | 66 | **67** |
| ⬆ | 14 | 14 |
| 🔁 | 13 | 13 |
| ⛔ | 8 | **7** |
| ❓ | 9 | 9 |

**#15 전자책 종류** — 명세의 "중간본(3권역)" 이 `INTERIM` 미구현으로 ⛔ 였다. 챕터 11 이 전자일기장으로 재도입하면서 ✅ 가 되었다. 다만 **명세와 뜻이 같지는 않다** — 명세는 "3권역", 이 저장소는 "3코스마다(3·6·9·12)" 다. 3코스마다는 예성 결정(결정 A)이고 그 결정이 명세를 대신한다.

---

## 7. [질문] · 남긴 것

1. **관리자 "후보 사찰 추가" API 가 없다.** 후보(`slot_site`)를 넣는 길은 시더 하나뿐이고, 관리자 코스 API 는 자리의 **대표 사찰**(`course_site`)만 다룬다. 챕터 11 은 엔드포인트를 늘리지 않기로 했으므로 위반을 막는 자리(`SlotSiteService.assign`)만 미리 만들어 두었다 — 그 API 를 열 때 이 메서드를 부르면 409 가 그대로 나온다. **언제 열 것인가는 열린 질문이다.**
2. **정확도 30m/100m 는 현장 실측 전 값이다.** 실측 뒤 `AccuracyGrade` 주석과 `frontend-handoff.md` 를 **함께** 고친다.
3. **`default-travel-minutes: 15` 는 첫 운영 값이다.** 240행이 채워지면 표가 우선하므로 이 값의 영향은 후보 사찰 쌍으로 줄어든다. 15분이 너무 빡빡하면 `PENDING(TRAVEL_TIME)` 이 늘어 관리자 일이 는다 — **배포 첫 주의 건수를 보고 조정할 값이다.**
4. **`user_reward.milestone` 은 지금 전자일기장만 쓴다.** 같은 정책으로 여러 번 받는 보상이 그것뿐이다. 다른 보상이 그런 성질을 갖게 되면 유니크가 이미 받쳐 준다.
5. **`api-types.d.ts` 에 선언 없이 참조되는 이름이 있다**(`EbookStampRow`·`EbookPhotoRow`·`EbookThinkboxRow`·`EbookCertRow`). 생성기가 `public record` 만 읽어서 Lombok `@Getter` 클래스를 놓친다. **챕터 11 이 만든 문제가 아니라 원래 그랬고**, 이 넷은 전부 내부 조회 DTO 라 프론트 계약에는 나가지 않는다. 생성기를 고칠지는 별건이다.
6. **`schema.sql` 주석 두 줄이 낡아 있었다** — `photo.is_private`·`thinkbox.is_private` 에 "전자책 제외" 라고 적혀 있었지만 챕터 9 결정은 **개인 소장본이라 싣는다** 였다. 코드는 처음부터 싣고 있었고 주석만 틀렸다. 고쳤다.
