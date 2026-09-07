# 챕터 4 마감 (v4 데이터화) — 실행 보고서

원문 `backend/docs/textbook/ch4-close.md` §4 확정 지시문 · STEP 0 ~ 6
실행일 2026-09-05 ~ 2026-09-06 · 루트 `src/` · git 없음 · 스택 고정 · 챕터 5 미착수

시드 적재 상세는 **`backend/docs/audit/seed-v4.md`** 에 따로 있다(권역표·좌표 0 목록·동명이찰·§3-2 결과).
이 문서는 무엇을 고쳤고 무엇이 남았는지를 본다.

---

## 1. CSV region_code ↔ DB region 매핑표

**이름 기준이고 DB 코드는 바꾸지 않았다.**

| CSV `region_code` | DB `code` | DB `name` | region_id | 시드 사찰 |
|---|---|---|---:|---:|
| SEOUL | SEOUL | 서울 | 1 | 9 |
| GYEONGGI | GYEONGGI | 경기·인천 | 2 | 16 |
| GANGWON | GANGWON | 강원 | 3 | 17 |
| CHUNGNAM_SEJONG | CHUNGNAM_SEJONG | 충남·세종 | **10** | 8 |
| CHUNGBUK | CHUNGBUK | 충북 | **11** | 6 |
| JEONBUK | JEONBUK | 전북 | 5 | 9 |
| JEONNAM_GWANGJU | **JEONNAM** | 전남 | 6 | 17 |
| DAEGU_GYEONGBUK | **GYEONGBUK** | 경북 | 7 | 12 |
| BUSAN_GYEONGNAM | **GYEONGNAM** | 경남 | 8 | 16 |
| — | JEJU | 제주 | 9 | 0 (준비 중) |

- **`CHUNGCHEONG`(충청, region_id 4) 삭제.** FK 참조 0건이라 그대로 지웠다. 충남·세종(10)·충북(11) 둘이
  후보 편성의 단위라 셋이 공존하면 "충청" 이 코스 없는 빈 껍데기로 남는다. **region_id 4 는 다시 쓰지 않는다.**
- **`JEJU` 유지.** 코스 0 이라 목록에만 나가고 화면은 "준비 중"(정리.md §4-6).
- **region 은 10행, 코스를 담은 권역은 9개.** 어긋나는 셋(굵게)은 `SiteSeedImporter.REGION_ALIAS` 세 줄.
- `regions_are_wrapped_in_items` 는 DB 기준으로 세고, `nine_regions_carry_courses` 가 "코스를 담은 권역 9" 를 지킨다.

---

## 2. 변경 파일

### 새로 만든 것 (16)

| 경로 | 무엇 |
|---|---|
| `course/SlotSite.java` · `SlotSiteMapper.java` · `mapper/course/SlotSiteMapper.xml` | 슬롯 후보 — upsert · exists · findByCourse · markCongested |
| `course/dto/SlotCandidateRow.java` · `dto/CandidateResponse.java` | 후보 조회 행 → 응답 record |
| `site/SiteElementMapper.java` · `mapper/site/SiteElementMapper.xml` | 사전 조회 · 보유 UPSERT · 삭제 |
| `admin/dto/SiteElementSaveRequest.java` · `admin/AdminSiteElementController.java` | 참배 요소 관리 API 2개 |
| `content/seed/SeedCsv.java` · `SiteSeedRow.java` · `SlotSeedRow.java` · `SiteSeedImporter.java` | v4 시더 (profile `seed`) |
| `resources/seed/sites-master.csv` · `slot-candidates.csv` | 시더가 읽는 사본 (정본은 `backend/docs/content/`) |
| `verify/seed-v4-check.sql` | 시드 검증 SQL 10절 |

### 고친 것

| 경로 | 무엇 |
|---|---|
| `db/schema.sql` | `slot_site` CREATE 추가(31 tables) · `stamp.site_id` · `site.seed_key` 를 CREATE 정의에 |
| `db/data.sql` | `CHUNGCHEONG` 삭제, `CHUNGNAM_SEJONG`(10)·`CHUNGBUK`(11) 추가 |
| `site/SiteGuideService.java` | **D3** — 이어 붙이기 제거. 각 자리가 자기 '오는 길' 한 문장 |
| `admin/AdminSiteService.java` | `upsertElements` · `deleteElement` · `createCourseForSeed` |
| `admin/AdminCourseService.java` | `position` 중복을 **COMMON-4000 + fields[sites]** 로 (전 404 COURSE-4042) |
| `course/CourseService.java` · `CourseController.java` · `dto/CourseSiteResponse.java` · `dto/CandidateResponse.java` | 자리마다 `candidates` · `?target=RIDER` 면 SUNROAD 우선 · 과포화는 `congested` 로 표시하고 뒤로 |
| `site/SiteMapper` · `course/CourseMapper` · `CourseSiteMapper` · `RegionMapper` (+XML) | 시더용 메서드 6개 |
| `global/config/RequiredEnvCheck.java` | prod 전용 필수값(`STORAGE_BUCKET`·`STORAGE_ENDPOINT`) |
| `build.gradle` | `com.opencsv:opencsv:5.9` |
| `postman/temple-stamp-all.postman_collection.json` | S10 · S10b · S11 (요소 API) |
| `verify/all-checkpoints.sql` | `site_element` 행 수 채점 1건 |

### 테스트 (108건, 전부 통과 — 챕터 시작 시 90건)

| 파일 | 건수 | 무엇 |
|---|---:|---|
| `content/seed/SeedConsistencyTest` (신규) | 8 | CSV↔DB 대조 · 가는 법 present · 라인 12×5 · 후보 무결성 2종 · 과포화만 있는 자리도 목록 안 빔 |
| `course/SlotSiteIntegrationTest` (신규) | 6 | exists(track 무관) · candidates · RIDER 정렬 · 과포화 표시 · 과포화 정렬 2종 |
| `admin/AdminSiteElementIntegrationTest` (신규) | 3 | 없는 코드 400 · 2회 UPSERT 1행 · 가는 법 즉시 반영 |
| `admin/AdminSiteCourseIntegrationTest` | +1 | `position` 중복 400 COMMON-4000 |
| `site/SitePageParamIntegrationTest` | — | `nine_regions_carry_courses` 추가(권역 수는 DB 기준) |
| `site/SiteGuideIntegrationTest` | — | 이름을 D3 확정 문구로 |

---

## 3. 시드 결과 요약

```
1회차  SEED DONE sites=110 created=110 courses=12 candidates=213 congestedRows=8 noCoord=18 report=1
2회차  SEED DONE sites=110 created=0   courses=0  candidates=213 congestedRows=8 noCoord=0  report=1   ← 멱등
```

| 표 | 시드분 | 기대 | |
|---|---:|---|---|
| `site` | 110 | 110 (묘각사 제외) | ✅ |
| `course` "○○ 공양의 길" | 12 | 12 | ✅ |
| `course_site` | 60 | 60 | ✅ |
| `slot_site` | 213 | 215 안팎 (묘각사 2행 제외) | ✅ |
| `site_element` | 342 | 조사값 있는 사찰분 | ✅ CSV 와 자리별 일치 |
| `site_badge` FLOWER | 24 | — | ✅ CSV 와 일치 |
| 좌표 0 | 18 | 목록화 | ✅ seed-v4.md §3 |

권역별 사찰 수·좌표 확보 수·동명이찰 표는 **seed-v4.md §2·§3·§6**.

### §3-2 정확성 검증 1~5

| # | 검증 | 결과 |
|---|---|---|
| 1 | CSV ↔ DB 대조 (Y 개수 · 주불전 이름 · FLOWER) | ✅ 전부 일치 |
| 2 | 가는 법 응답 `present` ↔ CSV Y/N | ✅ 표본 7칸 전부 일치 |
| 3 | 좌표 0 목록 | ✅ 18곳 (재확인 대상) |
| 4 | 동명이찰 | ✅ 7이름 15곳이 전부 다른 `seed_key` |
| 5 | 후보 무결성 (권역 일치 · MAIN 1 = 대표) | ✅ 어긋남 0행 |

---

## 4. 검증 세트

```
newman  요청 101/101 · 단언 420/420 · 실패 0
SQL     채점 9절 전부 기대값 (신규 site_element = 2 포함)
JUnit   108/108
```

`bash postman/run-all.sh` 는 정리→newman→SQL 채점→정리 순이라 몇 번을 돌려도 같은 결과가 나온다.
시드 데이터는 `cleanup.sql` 이 건드리지 않는다(검증용 이름만 지운다).

---

## 5. 교재와 다르게 한 것 — 이유와 함께

| # | 교재 | 저장소 | 왜 |
|---|---|---|---|
| 1 | D3 ③안 — 빈 구간 첫 자리에 '오는 길', 마지막에 '가는 길' | **②안 — 각 자리가 자기 '오는 길' 한 문장** | ③안대로 하면 빈 구간 마지막 자리의 '가는 길' 이 바로 다음(있는) 자리의 '오는 길' 과 같아 **여전히 겹치고**, 중간 문장이 사라져 수용 기준("P2~P6 한 번씩")과도 어긋난다. 교재 §0 D3 줄을 ②안으로 고쳤다 |
| 2 | `AdminSiteService` 본문 `status` 를 **무시** | **400 COMMON-4004** | `FAIL_ON_UNKNOWN_PROPERTIES` 가 켜져 있다. 조용히 무시하면 관리자는 공개했다고 믿는데 DRAFT 로 남는다 — 거절이 안전하다 |
| 3 | ~~`CompletionService` 회향 기준을 상수 `TOTAL_COURSES = 12`~~ | `courseService.countActiveCourses()` **유지**. **교재 문구를 "회향 = ACTIVE 코스 전부 완주(목표 12)" 로 고쳤다** | 지금 ACTIVE 코스는 데모 1개고 시드 12개는 DRAFT 다. 12를 박으면 회향이 영원히 성립하지 않고, 코스를 하나 내리면 조용히 깨진다. **"12" 는 목표값이지 판정식이 아니다** |
| 4 | `position` 중복 → 404 COURSE-4042 | **400 COMMON-4000 + fields[sites]** | COURSE-4042 는 "코스 자리를 찾을 수 없습니다" 다. 본문이 틀린 것을 조회 실패로 알리면 관리자가 무엇을 고칠지 알 수 없다 |
| 5 | 카카오 호출 **106회** | 실제 **110회** | 묘각사만 빠진다(111 − 1) |
| 6 | §3-2 2번 "조계사는 1~5 false" | CSV 기준으로 단언 | `sites-master.csv` 의 조계사는 `iljumun=Y`·`pagoda=Y` 다. **CSV 가 정본**이라 교재 문장이 아니라 CSV 를 기준으로 삼았다 |

---

## 6. [질문]

### ~~Q-a. 과포화 자리 2곳의 공개 후보가 0이 된다~~ → 해결 (2026-09-06, ②안 채택)

`[Q3 확정]` 4곳(통도사 서운암·해인사·범어사·백양사)이 모두 자기 자리의 **대표(MAIN sort 1)** 이고
둘은 **그 자리의 유일한 MAIN 후보**여서, `findByCourse` 가 `is_congested = 0` 으로 거르면
부산·동부 1·2구의 공개 후보가 0 이 됐다.

**결정: 숨기지 않고 "혼잡" 으로 표시한다.**

| 코스 | 자리 | 대표 | 처음 | 지금 |
|---|---:|---|---:|---:|
| 부산·동부 공양의 길 | 1구 | 통도사 서운암 | 0 | **2** (둘 다 `congested: true`) |
| 부산·동부 공양의 길 | 2구 | 범어사 | 0 | **2** (둘 다 `congested: true`) |

| 바뀐 곳 | 무엇 |
|---|---|
| `mapper/course/SlotSiteMapper.xml` | `WHERE` 에서 `ss.is_congested = 0` 제거, `SELECT` 에 `ss.is_congested AS congested` |
| `dto/SlotCandidateRow` · `dto/CandidateResponse` | 맨 뒤에 `congested` 필드 |
| `CourseService.candidatesOf` | 정렬 2차 기준 — **track 1차, congested 2차**. 같은 track 안에서 혼잡하지 않은 곳이 먼저 |
| 정리.md §4-6 · §4-9 | "`candidates[].congested = true` 는 과포화 — 목록에 남기되 '혼잡' 배지, 지도 강조 안 함" |
| 테스트 3건 | `SlotSiteIntegrationTest` 2(표시·정렬) · `SeedConsistencyTest` 1(과포화만 있는 자리도 목록이 비지 않는다) |

`slotSiteMapper.exists` 는 처음부터 `is_congested` 를 보지 않았으므로 **인증 경로는 바뀌지 않았다.**

### Q-b. 좌표 0인 18곳을 어떻게 채울 것인가

카카오 검색으로 못 찾은 산중 암자·동명이찰이다. **자동으로 채우지 않은 것이 맞다** — 반경 150m 안에서만
도장이 나가므로 틀린 좌표는 없는 좌표보다 나쁘다. 답사 때 함께 채우는지, 별도 좌표 조사표를 받는지.

### Q-c. 시드 사찰 110곳의 `qr_location_hint`

현장에서 정하는 값이라 비워 두었고, 그래서 **110곳 전부 DRAFT · 코스 12개도 DRAFT** 다.
공개하려면 사찰마다 힌트 한 줄이 필요하다(사찰 5곳이 전부 ACTIVE 여야 코스를 공개할 수 있다).
힌트 수집을 답사에 묶을지, 임시 문구로 먼저 열고 나중에 고칠지.

### Q-d. 조사 CSV 의 부속전각 다행(多行)

`annex_halls` 는 `각황전(국보67)·원통전·영산전…` 처럼 한 칸에 나열돼 있고 `site_element` 도 한 행이다.
정리.md §7-3 에 남아 있는 질의와 같은 건이다. 콘텐츠가 쌓이기 전에 정하는 편이 싸다.

Q1~Q4 는 교재 §0-1 로 확정됐으므로 다시 묻지 않는다. §0-2 A~D 도 확정(60 = 12코스 × 5).

---

## 7. 남은 준비

- 운영 DB ALTER 3건은 정리.md §5-1 에 그대로 있다(`slot_site` 는 신규 표라 CREATE 가 만든다).
- 회향은 **ACTIVE 코스 전부 완주**이고 목표가 12다. `countActiveCourses()` 를 그대로 쓴다 — 정리.md §5-3.
- 챕터 5 는 시작하지 않았다. `ch5.md` 에 미리 반영할 변경(교재 §5)은 그대로 남아 있다.

---

**newman 420/420, 기능 ✅ 10 · ⚠ 0 · ❌ 0 · — 3**

- ✅ 슬롯 후보 표·시더·요소 API·후보 응답·RIDER 정렬·**과포화 혼잡 표시**·D3 가는 법·region 재편·멱등·검증 세트
- ⚠ 없음 — Q-a 는 ②안(혼잡 표시)으로 닫았다
- — 좌표 0인 18곳(Q-b) · QR 힌트 미수집으로 전 사찰 DRAFT(Q-c) · 부속전각 다행(Q-d). 셋 다 코드가 아니라 **콘텐츠**가 필요한 건이다
