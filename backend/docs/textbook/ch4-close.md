# 챕터 4 마감 + 챕터 5 준비 — v4 「권역별 5구 확장 편성」 데이터화·시드·모델 변경

> 작성일 2026-09-06 · 스택 고정 · 교재 정본 원칙 · 파일 위치 `backend/docs/textbook/ch4-close.md`
> 입력 자료: ① 편성안 v3.2(12라인 60곳) ② **v4 확장안**(9권역 × 5구 후보 106곳 + 선로드 후보 105건) ③ 1차 조사(v3.2 60곳 7자리·주소·문화재) ④ fix-ch4-minor 보고
> 산출물: `backend/docs/content/sites-master.csv`(109행, 1차 36 + 2차 70 + 복귀 3) · `slot-candidates.csv`(213행) · `research60.csv`·`research70.csv`(조사 원본) · 이 문서의 DDL·Java·XML
> **CSV 4개는 전부 `backend/docs/content/`에 둔다(정본).** 시더가 읽는 사본은 Claude Code가 STEP 3에서 `src/main/resources/seed/`로 복사한다. 예성이 넣을 곳은 `backend/docs/content/` 한 곳뿐.

---

## 0. 결정 — 2026-09-06 확정

| # | 항목 | 결정 | 근거 |
|---|---|---|---|
| D1 | fix-ch4-minor: `status` 본문 → 400 vs 조용히 무시 | **400 유지.** `frontend-handoff.md`에 "등록·수정 본문에 status 금지, PATCH만" 추가 | 201 받고 공개됐다고 믿는 것이 더 위험. `FAIL_ON_UNKNOWN_PROPERTIES`를 켠 이유와 일치 |
| D2 | 서버 측 좌표 검증(의견91 §3) | **넣지 않는다. 원칙① 유지** | 좌표를 받는 순간 로그·전송 경로에 남고 위치정보 동의 범위가 바뀐다. 실물 QR + 이동시간 + 감사점수가 이미 "앱이 거짓말해도 도장은 안 찍힌다"를 보장. QR 유출은 회전(ver+1)이 담당 |
| D3 | 가는 법 연속 공백 반복(의견02 §1) | **②안 — 각 자리가 자기 '오는 길' 한 문장씩. 이어 붙이기 없음** (2026-09-06 정정: ③안은 빈 구간 마지막 자리의 '가는 길' 이 다음 자리의 '오는 길' 과 겹치고 중간 문장이 사라진다) | 도심 절이 이 기능이 가장 필요한 곳인데 거기서 가장 나쁘게 읽힌다. `SiteGuideService.toStep` 에서 이어 붙이기를 걷어내면 된다 |
| D4 | 사찰 요소 420개(→ v4는 106곳 × 7 = 742 판정) | **① 조사 시드(이 문서) + ② 관리자 API `POST /api/admin/sites/{id}/elements` + ③ 현장 답사 병행.** 미확인은 시드에 넣지 않는다 | "틀려도 화면이 정상"인 데이터라 추측을 섞지 않는다. 시드는 근거 있는 것만, 나머지는 관리자 화면으로 채운다 |
| D5 | **v4 확장안 채택 — 데이터 모델을 "슬롯 + 후보"로 바꾼다** | 코스 = **라인**(12개, 권역당 1~2개), 슬롯 = 5구, 슬롯마다 후보 사찰 N곳(MAIN 공양의 길 / SUNROAD 선로드). **한 슬롯에서 도장은 1개**, 다른 후보를 더 가면 사진 1장·문장 1개는 저장(개인 소장) | v4 문서의 규칙 그대로. 기존 `stamp.uk_stamp_completed(pilgrimage, slot)`이 "슬롯당 1개"를 이미 보장하므로 스키마 변경이 최소 |
| D6 | 최대 스탬프 수 | **45개(9권역 × 5구)로 계산된다.** v4 문서의 "60개"는 v3.2(12라인) 기준 수치 | ★ 60을 유지하려면 라인을 12개로 나눠야 하는데 v4 후보 풀은 권역 단위라 라인 배분이 없다. **[질문 Q1]** 45로 갈지, 강원(해안7·내륙8)·부산/경남(16)·서울·경기를 2~3라인으로 쪼개 60을 유지할지 |

### 0-1. 2026-09-06 예성 답변으로 확정된 것

| # | 결정 |
|---|---|
| Q1 규칙 | **1권역 1구 = 스탬프 1개.** 이미 발행된 구의 다른 후보에서 승인 요청 → **"이미 발행되었습니다" + 그 스탬프를 보여준다.** 대신 **사찰 1곳당 사진 1장 + 문장 1개**는 저장 허용(전자책에 포함). → §6 두 서비스 지시문 |
| Q2 | 묘각사(관음종): 서울 5구는 조계사·도선사로 성립 → **제외**. `sites-master.csv` note에 표기, 시드 미투입 |
| Q3 | 해인사·범어사·백양사·통도사 서운암: **후보 유지** + `slot_site.is_congested=1`(혼잡 시 비노출 플래그) |
| Q4 | (예성 위임) 2023 보물 사천왕상 3곳 **능가사(전남 2구)·흥국사 여수(전남 3구)·수타사(강원 1구) MAIN 후보로 복귀** + 12라인 성립용 **칠장사(경기 남부 3구)·천은사 삼척(강원 해안 4구)** 복귀. 나머지 19곳은 `research60.csv`에 예비 보관 |
| **A 확정** | **최대 스탬프 60 = 12라인 × 5구.** 9권역 중 부산/경남·강원·경기/인천을 2라인씩 나눔(§1-3 배분표). 코스(course) = 라인. 회향 = 12코스 60칸 |
| **B 확정** | 미확인 8곳(현덕사·망경산사·명주사·삼운사·용연사·영평사·학림사·서광사)은 후보로 넣되 **DRAFT 유지·ACTIVE 금지** |
| **C 확정** | 법륜사 교구는 비워 둠(전화 확인) |
| **D 확정** | v3.2 우량 사찰은 라인 성립에 필요한 2곳(칠장사·천은사 삼척)만 복귀, 나머지 예비 보관 |
| 고운사 | 복원 완료까지 DRAFT 고정 |

### 0-2. 아직 답이 필요한 것 — 예성 의견과 다르거나 미정인 것만

| # | 항목 | 예성 문서 | 내 의견 | 필요한 답 |
|---|---|---|---|---|
| A | 최대 스탬프 수 | 60개 | 9권역 × 5구 = **45개**. 60을 유지하려면 라인 12개로 쪼개야 하는데 v4 후보 풀은 권역 단위라 라인 배분이 없음 | **45 확정** 또는 12라인 배분표 제공 |
| B | 종단·주불전 전면 미확인 8곳(현덕사·망경산사·명주사·삼운사·용연사·영평사·학림사·서광사) | 후보에 포함 | 조계종 확인 전까지 **DRAFT로 넣되 ACTIVE 금지**. 특히 강원 1구는 현덕사·망경산사 둘뿐이라 둘 다 빠지면 1구가 비므로 제외하지 않음 | 동의 여부 |
| C | 법륜사(용인) 교구 상충(제2교구 vs 제7교구) | — | 자료 상충이라 `diocese` 비워 둠. 사찰에 전화 확인 | 동의 여부 |
| D | 부석사 영주·정암사·자재암·보광사·칠장사 등 v3.2 우량 사찰 21곳 | v4에 없음 | 콘텐츠(국보·가는 법 확인값)가 좋아 아깝지만 **예성 편성 존중 → 예비 보관** | 복귀시킬 곳이 있으면 이름만 |

A·B·C·D 답이 오면 사찰 선정은 종결이고, 아래 지시문 그대로 코딩에 들어간다.

**[질문 Q2]** 묘각사(종로 숭인동)는 관음종 총본산으로 알려져 v3.2에서 제외됐는데 v4 서울 5구에 다시 있다. 조계종 단일 원칙(v3.2 §0)을 유지하면 빼야 한다. **[질문 Q3]** 해인사·범어사·백양사·통도사(서운암)는 v3.2에서 과포화(티맵 상위·90만 초과)로 관람 배지 처리했는데 v4 후보에 있다 — 후보로 넣되 "혼잡 시 비노출" 플래그로 갈지, 제외할지. 셋 다 답이 없으면 **후보에는 넣고 `note`에 표기·DRAFT 유지**로 진행한다.

---

## 1. v4 데이터화 결과

### 1-1. 후보 풀 `slot-candidates.csv` — 210행 (MAIN 106 · SUNROAD 105 → 중복 제거 후 사찰 106곳)

열: `region_code, region_name, verse_no, site_name, disambiguation, track(MAIN/SUNROAD), serving_note(배정 근거), sunroad_route(노선), sunroad_star(★), note`

권역 코드는 임시값이다 — Claude Code가 `SELECT region_id, code, name FROM region ORDER BY sort_no`로 실제 코드에 매핑한다(§4 STEP 1).

| region_code | 권역 | MAIN 후보 | 비고 |
|---|---|---|---|
| SEOUL | 서울 | 10 | 묘각사 종단 [Q2] |
| GYEONGGI | 경기/인천 | 15 | |
| BUSAN_GYEONGNAM | 부산/경남 | 16 | 해인사·범어사·서운암 과포화 [Q3] |
| DAEGU_GYEONGBUK | 대구/경북 | 12 | 고운사 복원 중 → DRAFT 고정 |
| GANGWON | 강원 | 15 | 해안7·내륙8 |
| JEONNAM_GWANGJU | 전남/광주 | 15 | 백양사 과포화 [Q3] |
| JEONBUK | 전북 | 9 | |
| CHUNGNAM_SEJONG | 충남/세종 | 8 | 신규 권역 |
| CHUNGBUK | 충북 | 6 | 신규 권역 |

**v3.2와의 관계**: v3.2 60곳 중 v4에 남은 것 36곳(조사값 그대로 사용), 빠진 것 24곳(미타사·흥천사·청룡사·개운사·삼천사·법룡사·칠장사·흥국사 남양주·자재암·보광사·정수사·백련사 강화·문수사·용궁사·장안사·운수사·마하사·석남사·능가사·흥국사 여수·부석사 영주·수타사·정암사·천은사 삼척)은 `research60.csv`에 남겨 **예비 후보**로 보관. 특히 능가사·흥국사(여수)·수타사는 2023 보물 사천왕상 보유라 후보 복귀를 권한다 **[질문 Q4]**.

### 1-3. 12라인 배분표 (A 확정) — `slot-candidates.csv`의 `line_code`·`line_name`

| # | 권역 | 라인(코스) | line_code | 1구 출처 | 2구 감사 | 3구 절제 | 4구 포행 | 5구 다짐 |
|---|---|---|---|---|---|---|---|---|
| 1 | 서울 | 서울 | SEOUL | 진관사 | 국제선센터·화계사 | 길상사·금선사 | 경국사·수국사 | 조계사·도선사 |
| 2 | 경기/인천 | 경기 남부 | GYEONGGI_S | 봉녕사·수도사 | 법륜사 | **칠장사(복귀)** | 용문사 양평·연주암 | 용주사·신륵사 |
| 3 | 경기/인천 | 경기 북부·인천 | GYEONGGI_N | 봉선사 | 연등국제선원·봉인사 | 묘적사·백련사 가평·회암사 | 흥국사 고양 | 전등사 |
| 4 | 부산/경남 | 부산·동부 | BUSAN_E | 통도사 서운암 | 범어사 | 내원정사·선암사 | 표충사·성주사 | 홍법사 |
| 5 | 부산/경남 | 경남 서부 | GYEONGNAM_W | 금수암·다솔사·쌍계사 | 해인사·대원사 산청 | 옥천사 | 문수암 | 용문사 남해·용화사 통영 |
| 6 | 대구/경북 | 대구/경북 | DAEGU_GYEONGBUK | 용문사 예천·동화사 | 은해사·봉정사·축서사 | 고운사(DRAFT)·희방사 | 보경사·기림사 | 직지사·도리사·골굴사 |
| 7 | 강원 | 강원 해안 | GANGWON_COAST | 현덕사 | 보현사 | **삼화사(4→3구)** | 용연사·화암사·**천은사 삼척(복귀)** | 신흥사·건봉사 |
| 8 | 강원 | 강원 내륙 | GANGWON_INLAND | 망경산사·수타사 | 백담사 | 청평사·명주사·삼운사 | **월정사(2→4구)** | 구룡사·법흥사 |
| 9 | 전남/광주 | 전남/광주 | JEONNAM_GWANGJU | 백양사·백련사 강진·대흥사 | 송광사 순천·화엄사·도갑사·능가사 | 무위사·운주사·증심사·흥국사 여수 | 천은사 구례·대원사 보성·무각사 | 미황사·불갑사·향일암 |
| 10 | 전북 | 전북 | JEONBUK | 실상사 | 송광사 완주·귀정사 | 내소사·개암사 | 금당사·안국사 | 금산사·선운사 |
| 11 | 충남/세종 | 충남/세종 | CHUNGNAM_SEJONG | 영평사·학림사 | 수덕사·마곡사 | 무량사·서광사 | 갑사 | 부석사 서산 |
| 12 | 충북 | 충북 | CHUNGBUK | 미륵대흥사 | 석종사 | 반야사 | 용화사 청주 | 법주사·영국사 |

**왜 이 세 권역만 쪼갰나**: 라인마다 5구가 모두 있어야 한다. 서울(1구 진관사 1곳)·경북(북부에 4구 없음)·전남(동부에 1구 없음)·전북·충남·충북은 쪼개면 빈 구가 생긴다. 부산/경남은 동서로 깨끗이 나뉘고, 강원은 v3.2 배정(삼화사 3구·월정사 4구)을 되살리면 해안·내륙이 성립하며, 경기는 남부 3구에 칠장사 한 곳만 복귀하면 된다. 선로드(SUNROAD) 후보는 같은 사찰의 라인을 따른다. 시더는 `line_code`별로 코스 12개를 만든다.

### 1-2. 사찰 마스터 `sites-master.csv` — 111행

열: `region_code, site_name_ko, disambiguation, site_name_en, road_address, latitude, longitude, diocese, research_status, iljumun…pagoda(Y/N/?), *_name, main_hall_name, annex_halls, parking_info, access_info, meal_available, flower_badge, sources, note`

| research_status | 수 | 시드 처리 |
|---|---|---|
| 1차조사(v3.2) | 36 | 7자리 Y·이름·주불전·부속·주차·꽃 → 시드. `?`는 넣지 않음 |
| 2차조사 필요 | 70 | 이름·권역·구·구분만 시드(DRAFT). 7자리 전부 미확인 → **관리자 API 또는 2차 조사로** |

좌표: 전부 카카오로 채운다(§3 시더). CSV 좌표는 참고값 4곳뿐.

---

## 2. 데이터 모델 v4 — 슬롯과 후보

```
region(9) ─1:1─ course("○○ 공양의 길") ─1:5─ course_site(슬롯: position=verse_no, site_id=대표 후보)
                                                   └─1:N─ slot_site(후보: site_id, track MAIN|SUNROAD, sort_no, route_note, is_star)
pilgrimage(user × course) ─1:N─ stamp(course_site_id=슬롯, site_id=실제 인증 사찰 ★신규, completed_course_site_id 생성컬럼 UNIQUE → 슬롯당 도장 1개)
photo(uk user+site) · thinkbox(site_id) → 도장 없이도 후보 사찰마다 사진 1·문장 1 저장 (개인 소장) — 기존 표로 충족, 신규 없음
```

### 2-1. `db/schema.sql` append (CREATE TABLE stamp·site 정의는 수정, 신규 표 1개)

```sql
-- ── v4: 슬롯 후보 ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS slot_site (
    slot_site_id   BIGINT       NOT NULL AUTO_INCREMENT,
    course_site_id BIGINT       NOT NULL                    COMMENT '슬롯(코스의 자리 = 구)',
    site_id        BIGINT       NOT NULL                    COMMENT '후보 사찰',
    track          VARCHAR(10)  NOT NULL DEFAULT 'MAIN'     COMMENT 'MAIN(공양의 길) / SUNROAD(선로드)',
    sort_no        INT          NOT NULL DEFAULT 0          COMMENT '후보 나열 순서(1=대표)',
    route_note     VARCHAR(255) NULL                        COMMENT '선로드 노선(배내고개·에덴밸리 등)',
    is_star        TINYINT(1)   NOT NULL DEFAULT 0          COMMENT '선로드 ★ 표시',
    serving_note   VARCHAR(255) NULL                        COMMENT '배정 근거(장독 5,000개 등)',
    is_congested   TINYINT(1)   NOT NULL DEFAULT 0          COMMENT 'Q3: 과포화 사찰 — 1이면 공개 응답에서 비노출(관리자는 보임)',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (slot_site_id),
    CONSTRAINT uk_slot_site UNIQUE (course_site_id, site_id, track),
    INDEX idx_slot_site_site (site_id),
    CONSTRAINT fk_slot_site_course_site_id FOREIGN KEY (course_site_id) REFERENCES course_site (course_site_id) ON DELETE RESTRICT,
    CONSTRAINT fk_slot_site_site_id        FOREIGN KEY (site_id)        REFERENCES site (site_id)               ON DELETE RESTRICT,
    CONSTRAINT chk_slot_site_track CHECK (track IN ('MAIN','SUNROAD'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- ※ 한 사찰이 여러 권역·구의 후보가 될 수 있다(대원사 산청: 부산/경남 2구 MAIN+SUNROAD). course_site.site_id(대표)는 uk_course_site_site 로 계속 유일.

-- stamp: 실제 인증한 후보 사찰 (CREATE TABLE stamp 정의 안에 추가 — mission_id 뒤)
--   site_id BIGINT NULL COMMENT 'v4: 실제 인증한 후보 사찰. 완료 도장의 유일성은 여전히 슬롯 기준(uk_stamp_completed)',
--   CONSTRAINT fk_stamp_site_id FOREIGN KEY (site_id) REFERENCES site (site_id) ON DELETE RESTRICT,
-- site: 시드 멱등 키 (CREATE TABLE site 정의 안에 추가 — status 뒤)
--   seed_key VARCHAR(80) NULL COMMENT '시드 재실행 시 중복 방지: REGION:이름:구분', CONSTRAINT uk_site_seed_key UNIQUE (seed_key),
-- 로컬 DB 1회 ALTER (배포 체크리스트에도 추가):
--   ALTER TABLE stamp ADD COLUMN site_id BIGINT NULL AFTER mission_id, ADD CONSTRAINT fk_stamp_site_id FOREIGN KEY (site_id) REFERENCES site(site_id) ON DELETE RESTRICT;
--   ALTER TABLE site  ADD COLUMN seed_key VARCHAR(80) NULL AFTER status, ADD UNIQUE KEY uk_site_seed_key (seed_key);
```

### 2-2. 규칙 변경(챕터 5·6에 반영)

| 규칙 | v3 | v4 |
|---|---|---|
| GPS 1단계 입력 | `courseSiteId`(경로) | `courseSiteId`(경로) + 본문 **`siteId`**(후보). `slot_site`에 (슬롯, 사찰)이 없으면 400 `COURSE-4001`("다른 코스의 자리") |
| 도장 유일성 | 슬롯당 1개 | **동일** (`uk_stamp_completed`) — 다른 후보로 다시 gps-check 하면 409 이미 완료 |
| 후보 추가 방문 | — | 도장 없이 `PUT /api/photos/{siteId}`(F-12) + `POST /api/thinkbox {siteId}` 허용 — 기존 API, 신규 없음 |
| 선로드 | 별도 코스(T4) | 같은 슬롯의 `track=SUNROAD` 후보. tier `RIDER`면 싱글페이지·코스 상세에서 SUNROAD 후보 우선 정렬(`route_note` 노출) |
| 코스 상세 응답 | 슬롯당 사찰 1 | `CourseSiteResponse`에 `candidates: [ {siteId, name, track, routeNote, isStar} ]` 추가(대표는 그대로 최상위 필드) |
| 완주 | 5칸 COMPLETED | 동일. 회향 = 전 권역 완주(9코스 45칸, Q1 답에 따라 12/60) |
| 이동시간 | course_site 쌍 | **site_distance는 실제 사찰 쌍(site_id)** 그대로 — 후보가 늘어도 표 구조 불변. 없으면 `defaultMinTravelMinutes` |

---

## 3. 시드 파이프라인 — 코드 전문 (교재 정본)

CSV 두 개를 읽어 카카오로 좌표를 채우고 `AdminSiteService`(ko 필수·DRAFT) → 요소·뱃지 → 코스·슬롯·후보 순으로 넣는다. `--spring.profiles.active=local,seed`로 1회 실행. 멱등(`site.seed_key`, `INSERT IGNORE`).

### [기본 49] 도메인·Mapper — SlotSite · SiteElement

```java
// src/main/java/com/templestamp/course/SlotSite.java
package com.templestamp.course;

import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;

/** slot_site 1행 — 한 슬롯(course_site)의 후보 사찰 하나. track 은 MAIN/SUNROAD 문자열 */
@Getter @Setter @NoArgsConstructor
public class SlotSite {
    private Long slotSiteId;
    private Long courseSiteId;
    private Long siteId;
    private String track;
    private Integer sortNo;
    private String routeNote;
    private Boolean isStar;
    private String servingNote;
}
```
```java
// src/main/java/com/templestamp/course/SlotSiteMapper.java
package com.templestamp.course;

import com.templestamp.course.dto.SlotCandidateRow;
import org.apache.ibatis.annotations.Mapper; import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SlotSiteMapper {
    int upsert(SlotSite s);                                                                  // uk(course_site_id, site_id, track)
    boolean exists(@Param("courseSiteId") Long courseSiteId, @Param("siteId") Long siteId); // GPS 1단계 검증 — track 무관
    List<SlotCandidateRow> findByCourse(@Param("courseId") Long courseId, @Param("locale") String locale);   // 코스 상세 candidates
}
```
```xml
<!-- src/main/resources/mapper/course/SlotSiteMapper.xml -->
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.templestamp.course.SlotSiteMapper">
  <insert id="upsert">
    INSERT INTO slot_site (course_site_id, site_id, track, sort_no, route_note, is_star, serving_note)
    VALUES (#{courseSiteId}, #{siteId}, #{track}, #{sortNo}, #{routeNote}, #{isStar}, #{servingNote})
    ON DUPLICATE KEY UPDATE sort_no = VALUES(sort_no), route_note = VALUES(route_note), is_star = VALUES(is_star), serving_note = VALUES(serving_note)
    <!-- [RULE] 삭제 후 재삽입 금지. 후보를 빼려면 관리자 API 의 개별 DELETE 로 -->
  </insert>
  <select id="exists" resultType="boolean">
    SELECT COUNT(*) > 0 FROM slot_site WHERE course_site_id = #{courseSiteId} AND site_id = #{siteId}
  </select>
  <select id="findByCourse" resultType="com.templestamp.course.dto.SlotCandidateRow">
    SELECT ss.course_site_id AS courseSiteId, ss.site_id AS siteId, COALESCE(si.name, s.name) AS siteName,
           ss.track AS track, ss.sort_no AS sortNo, ss.route_note AS routeNote, ss.is_star AS isStar, ss.serving_note AS servingNote,
           s.status AS siteStatus
      FROM slot_site ss
      JOIN course_site cs ON cs.course_site_id = ss.course_site_id AND cs.course_id = #{courseId}
      JOIN site s ON s.site_id = ss.site_id
      LEFT JOIN site_i18n si ON si.site_id = s.site_id AND si.locale = #{locale}
     WHERE s.status = 'ACTIVE' AND ss.is_congested = 0   <!-- 공개 응답: ACTIVE·비혼잡 후보만. 관리자는 별도 조회 -->
     ORDER BY cs.position, ss.track, ss.sort_no
  </select>
</mapper>
```
```java
// src/main/java/com/templestamp/course/dto/SlotCandidateRow.java
package com.templestamp.course.dto;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
@Getter @Setter @NoArgsConstructor
public class SlotCandidateRow { private Long courseSiteId; private Long siteId; private String siteName; private String track; private Integer sortNo; private String routeNote; private Boolean isStar; private String servingNote; private String siteStatus; }

// src/main/java/com/templestamp/course/dto/CandidateResponse.java
package com.templestamp.course.dto;
/** CourseSiteResponse.candidates 의 한 건. 대표 사찰도 candidates 에 포함된다(track MAIN, sortNo 1) */
public record CandidateResponse(Long siteId, String siteName, String track, Integer sortNo, String routeNote, boolean isStar, String servingNote) {
    public static CandidateResponse from(SlotCandidateRow r) {
        return new CandidateResponse(r.getSiteId(), r.getSiteName(), r.getTrack(), r.getSortNo(), r.getRouteNote(), Boolean.TRUE.equals(r.getIsStar()), r.getServingNote());
    }
}
```
`CourseSiteResponse`에 `List<CandidateResponse> candidates` 필드 추가(맨 뒤). `CourseService.getCourseDetail`이 `slotSiteMapper.findByCourse`를 한 번 읽어 슬롯별로 붙인다. 사용자 tier가 RIDER면 SUNROAD를 앞으로 정렬(Java에서).

```java
// src/main/java/com/templestamp/site/SiteElementMapper.java
package com.templestamp.site;
import org.apache.ibatis.annotations.Mapper; import org.apache.ibatis.annotations.Param;
import java.util.List; import java.util.Map;

@Mapper
public interface SiteElementMapper {
    List<Map<String, Object>> findDictionary();                                             // shrine_element: element_id, code, sort_no, name
    int upsert(@Param("siteId") Long siteId, @Param("elementId") Long elementId, @Param("localName") String localName, @Param("note") String note);
    int delete(@Param("siteId") Long siteId, @Param("elementId") Long elementId);
}
```
```xml
<!-- src/main/resources/mapper/site/SiteElementMapper.xml -->
<mapper namespace="com.templestamp.site.SiteElementMapper">
  <select id="findDictionary" resultType="map">
    SELECT element_id AS elementId, code, sort_no AS sortNo, name FROM shrine_element ORDER BY sort_no
  </select>
  <insert id="upsert">
    INSERT INTO site_element (site_id, element_id, local_name, note) VALUES (#{siteId}, #{elementId}, #{localName}, #{note})
    ON DUPLICATE KEY UPDATE local_name = VALUES(local_name), note = VALUES(note)
    <!-- uk_site_element(site_id, element_id). 부속전각·탑 다행(의견02 §6)은 아직 미채택 — 여러 이름은 local_name 에 '·' 로 -->
  </insert>
  <delete id="delete">DELETE FROM site_element WHERE site_id = #{siteId} AND element_id = #{elementId}</delete>
</mapper>
```
> `site_element` 컬럼명(`local_name`·`note`)은 챕터 2 v2 델타 기준. Claude Code가 STEP 1에서 실제 컬럼과 대조한다.

### [기본 50] 관리자 요소 API — D4 ②

```java
// src/main/java/com/templestamp/admin/dto/SiteElementSaveRequest.java
package com.templestamp.admin.dto;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import java.util.List;
/** 사찰의 7자리 중 "있는 것"만 보낸다. 보내지 않은 자리는 건드리지 않는다(null=변경 없음). 없앨 때는 DELETE /elements/{code} */
public record SiteElementSaveRequest(@NotEmpty @Size(max = 7) List<@Valid Item> items) {
    public record Item(@NotBlank @Size(max = 30) String elementCode,          // ILJUMUN 등 shrine_element.code
                       @NotBlank @Size(max = 100) String localName,           // 그 절에서 부르는 이름
                       @Size(max = 255) String note) {}
}
```
```java
// src/main/java/com/templestamp/admin/AdminSiteElementController.java (AdminSiteController 에 메서드 2개로 합쳐도 됨)
package com.templestamp.admin;
import com.templestamp.admin.dto.SiteElementSaveRequest;
import com.templestamp.global.response.ApiResponse;
import jakarta.validation.Valid; import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/sites") @RequiredArgsConstructor @Validated
public class AdminSiteElementController {
    private final AdminSiteService adminSiteService;

    @PostMapping("/{siteId}/elements")
    public ApiResponse<Void> upsert(@PathVariable @Positive Long siteId, @RequestBody @Valid SiteElementSaveRequest req) {
        adminSiteService.upsertElements(siteId, req);   // 사찰 존재(SITE-4040) · 코드 존재(COMMON-4000 fields[elementCode]) 검사 후 UPSERT
        return ApiResponse.ok();
    }

    @DeleteMapping("/{siteId}/elements/{elementCode}")
    public ApiResponse<Void> delete(@PathVariable @Positive Long siteId, @PathVariable String elementCode) {
        adminSiteService.deleteElement(siteId, elementCode);
        return ApiResponse.ok();
    }
}
```
`AdminSiteService.upsertElements`: `siteMapper.findByIdAnyStatus` → `siteElementMapper.findDictionary()`로 code→elementId 맵 → 없는 코드는 `COMMON_4000` + `fields[elementCode]` → `upsert`. 확인포인트: 없는 코드 400 / 같은 코드 2회 → `site_element` 1행 / `GET /api/sites/{id}/guide`에 즉시 반영.

### [기본 51] 시더 — SiteSeedImporter (profile `seed`, 1회 실행)

```java
// src/main/java/com/templestamp/content/seed/SiteSeedRow.java
package com.templestamp.content.seed;
/** sites-master.csv 한 줄. 비어 있으면 null, "?"·"미확인" 도 null 로 읽는다(시드에 넣지 않기 위해) */
public record SiteSeedRow(String regionCode, String nameKo, String disambiguation, String nameEn, String roadAddress,
                          Double latitude, Double longitude, String diocese, String researchStatus,
                          String iljumun, String iljumunName, String geumgangmun, String geumgangmunName,
                          String cheonwangmun, String cheonwangmunName, String bulimun, String bulimunName,
                          String pagoda, String pagodaName, String mainHallName, String annexHalls,
                          String parkingInfo, String accessInfo, String mealAvailable, String flowerBadge, String sources, String note) {
    public String seedKey() { return regionCode + ":" + nameKo + ":" + (disambiguation == null ? "" : disambiguation); }
}

// src/main/java/com/templestamp/content/seed/SlotSeedRow.java
package com.templestamp.content.seed;
public record SlotSeedRow(String regionCode, String regionName, int verseNo, String siteName, String disambiguation,
                          String track, String servingNote, String sunroadRoute, boolean star, String note) {
    public String siteSeedKey() { return regionCode + ":" + siteName + ":" + (disambiguation == null ? "" : disambiguation); }
}
```
```java
// src/main/java/com/templestamp/content/seed/SeedCsv.java
package com.templestamp.content.seed;

import com.opencsv.CSVReaderHeaderAware;   // build.gradle: implementation 'com.opencsv:opencsv:5.9'
import org.springframework.core.io.ClassPathResource;
import java.io.InputStreamReader; import java.nio.charset.StandardCharsets; import java.util.*;

/** UTF-8(BOM 허용) CSV → 헤더 기반 Map 목록. 값 정리: "" · "?" · "미확인" 로 시작하는 값 → null */
final class SeedCsv {
    static List<Map<String, String>> read(String classpath) throws Exception {
        try (var r = new CSVReaderHeaderAware(new InputStreamReader(new ClassPathResource(classpath).getInputStream(), StandardCharsets.UTF_8))) {
            List<Map<String, String>> out = new ArrayList<>(); Map<String, String> row;
            while ((row = r.readMap()) != null) { Map<String, String> c = new HashMap<>(); row.forEach((k, v) -> c.put(k.replace("\uFEFF", "").trim(), clean(v))); out.add(c); }
            return out;
        }
    }
    static String clean(String v) {
        if (v == null) return null; v = v.trim();
        return (v.isEmpty() || v.equals("?") || v.startsWith("미확인")) ? null : v;
    }
    static Double num(String v) { return v == null ? null : Double.valueOf(v); }
    static boolean yes(String v) { return "Y".equalsIgnoreCase(v); }
    private SeedCsv() {}
}
```
```java
// src/main/java/com/templestamp/content/seed/SiteSeedImporter.java
package com.templestamp.content.seed;

import com.templestamp.admin.AdminSiteService;
import com.templestamp.admin.dto.AdminCourseSaveRequest;
import com.templestamp.admin.dto.AdminSiteSaveRequest;
import com.templestamp.admin.dto.SiteBadgeSaveRequest;
import com.templestamp.course.*;
import com.templestamp.kakao.KakaoMapService;
import com.templestamp.kakao.dto.KakaoPlaceResponse;
import com.templestamp.site.SiteElementMapper;
import com.templestamp.site.SiteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * v4 시드 적재기. `--spring.profiles.active=local,seed` 로 기동하면 한 번 돌고 끝난다(운영 프로파일에는 절대 없음).
 * 순서: 사찰(ko 필수·DRAFT) → 좌표(카카오) → 요소·뱃지 → 권역별 코스 1개 + 슬롯 5 + 후보 N.
 * 멱등: site.seed_key 로 재실행 시 기존 행을 쓰고, 요소·후보는 UPSERT/INSERT IGNORE.
 * 넣지 않는 것: 7자리 '?'·'미확인', 좌표 못 찾은 사찰의 좌표(0,0 으로 두고 보고서에 남김 — ACTIVE 전환은 관리자가 좌표 확인 후).
 */
@Component
@Profile("seed")
@RequiredArgsConstructor
@Slf4j
public class SiteSeedImporter implements CommandLineRunner {

    private static final int DEFAULT_RADIUS = 150;   // 실사 전 임시 반경(m). 관리자 PUT 으로 교정

    private final AdminSiteService adminSiteService;
    private final SiteMapper siteMapper;
    private final SiteElementMapper siteElementMapper;
    private final RegionMapper regionMapper;
    private final CourseMapper courseMapper;
    private final CourseSiteMapper courseSiteMapper;
    private final SlotSiteMapper slotSiteMapper;
    private final KakaoMapService kakao;

    @Override
    public void run(String... args) throws Exception {
        List<Map<String, String>> sites = SeedCsv.read("seed/sites-master.csv");
        List<Map<String, String>> slots = SeedCsv.read("seed/slot-candidates.csv");
        Map<String, Long> regionByCode = regionMapper.findAll().stream().collect(java.util.stream.Collectors.toMap(r -> r.getCode(), r -> r.getRegionId()));
        Map<String, Long> elementBySort = new HashMap<>();
        siteElementMapper.findDictionary().forEach(m -> elementBySort.put(String.valueOf(m.get("sortNo")), ((Number) m.get("elementId")).longValue()));

        Map<String, Long> siteIdBySeedKey = new HashMap<>();
        List<String> noCoord = new ArrayList<>(), report = new ArrayList<>();

        // ── 1) 사찰 ──────────────────────────────────────────────────────────
        for (var r : sites) {
            String key = r.get("region_code") + ":" + r.get("site_name_ko") + ":" + Objects.toString(r.get("disambiguation"), "");
            Long siteId = siteMapper.findIdBySeedKey(key);
            if (siteId == null) {
                BigDecimal[] ll = resolveCoord(r);                                   // 카카오 → CSV → (0,0)
                if (ll == null) { ll = new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO}; noCoord.add(key); }
                var i18n = new ArrayList<AdminSiteSaveRequest.I18nBlock>();
                i18n.add(new AdminSiteSaveRequest.I18nBlock("ko", r.get("site_name_ko"), describe(r)));
                if (r.get("site_name_en") != null) i18n.add(new AdminSiteSaveRequest.I18nBlock("en", r.get("site_name_en"), null));
                var req = new AdminSiteSaveRequest(ll[0], ll[1], DEFAULT_RADIUS, null,           // qrLocationHint 는 현장 지정 → null → ACTIVE 불가(의도)
                        r.get("parking_info"), r.get("access_info"), r.get("meal_available"), i18n);
                siteId = adminSiteService.create(req).siteId();
                siteMapper.updateSeedKey(siteId, key);
            }
            siteIdBySeedKey.put(key, siteId);

            // 7자리 — Y 인 것만. 이름이 없으면 사전 이름(shrine_element.name)으로 채우지 않고 빈 문자열 대신 null → 관리자 보완
            putElement(siteId, elementBySort.get("1"), r, "iljumun");
            putElement(siteId, elementBySort.get("2"), r, "geumgangmun");
            putElement(siteId, elementBySort.get("3"), r, "cheonwangmun");
            putElement(siteId, elementBySort.get("4"), r, "bulimun");
            putElement(siteId, elementBySort.get("5"), r, "pagoda");
            if (r.get("main_hall_name") != null) siteElementMapper.upsert(siteId, elementBySort.get("6"), r.get("main_hall_name"), null);   // 주불전은 모든 절에 있다
            if (r.get("annex_halls") != null)    siteElementMapper.upsert(siteId, elementBySort.get("7"), r.get("annex_halls"), null);
            if (r.get("flower_badge") != null)   adminSiteService.upsertBadge(siteId, new SiteBadgeSaveRequest("FLOWER", r.get("flower_badge")));
        }

        // ── 2) 라인별 코스 1개(12개) + 슬롯 5 + 후보 ───────────────────────────
        Map<String, List<Map<String, String>>> byLine = new LinkedHashMap<>();          // ★ 라인(line_code)별 코스 — 12개
        slots.forEach(s -> byLine.computeIfAbsent(s.get("line_code"), k -> new ArrayList<>()).add(s));
        for (var e : byLine.entrySet()) {
            Long regionId = regionByCode.get(e.getValue().get(0).get("region_code"));
            if (regionId == null) { report.add("권역 코드 없음: " + e.getValue().get(0).get("region_code")); continue; }
            String courseName = e.getValue().get(0).get("line_name") + " 공양의 길";   // 예: "경기 남부 공양의 길"
            Long courseId = courseMapper.findIdByRegionAndName(regionId, courseName);
            if (courseId == null) {
                // 대표 후보 = 구별 MAIN 첫 행. 5구 모두 있어야 AdminCourseSaveRequest 검증을 통과한다
                var reps = new ArrayList<AdminCourseSaveRequest.SiteSlot>();
                for (int v = 1; v <= 5; v++) {
                    final int verse = v;
                    var first = e.getValue().stream().filter(s -> "MAIN".equals(s.get("track")) && Integer.parseInt(s.get("verse_no")) == verse).findFirst().orElseThrow();
                    reps.add(new AdminCourseSaveRequest.SiteSlot(siteIdBySeedKey.get(seedKey(first)), verse, verse));
                }
                courseId = adminSiteService.createCourseForSeed(new AdminCourseSaveRequest(regionId, courseName, reps));   // AdminCourseService.create 위임(DRAFT)
            }
            Map<Integer, Long> slotByVerse = courseSiteMapper.findSlotIdsByCourse(courseId);   // position → course_site_id
            for (var s : e.getValue()) {
                Long siteId = siteIdBySeedKey.get(seedKey(s));
                if (siteId == null) { report.add("후보 사찰 미등록: " + seedKey(s)); continue; }
                SlotSite ss = new SlotSite();
                ss.setCourseSiteId(slotByVerse.get(Integer.parseInt(s.get("verse_no"))));
                ss.setSiteId(siteId); ss.setTrack(s.get("track"));
                ss.setSortNo(sortOf(e.getValue(), s)); ss.setRouteNote(s.get("sunroad_route"));
                ss.setIsStar("Y".equalsIgnoreCase(s.get("sunroad_star"))); ss.setServingNote(s.get("serving_note"));
                slotSiteMapper.upsert(ss);
            }
        }
        log.info("SEED DONE sites={} noCoord={} report={}", siteIdBySeedKey.size(), noCoord.size(), report.size());
        noCoord.forEach(k -> log.warn("SEED no-coord {}", k));
        report.forEach(m -> log.warn("SEED {}", m));
    }

    private BigDecimal[] resolveCoord(Map<String, String> r) {
        try {
            String q = r.get("site_name_ko") + " " + Objects.toString(r.get("disambiguation"), "");
            var res = kakao.search(q, 1, 3);
            for (KakaoPlaceResponse p : res.places())                                // 카테고리에 '사찰' 이 있는 첫 결과. 없으면 첫 결과
                if (p.categoryName() != null && p.categoryName().contains("사찰")) return new BigDecimal[]{p.latitude(), p.longitude()};
            if (!res.places().isEmpty()) return new BigDecimal[]{res.places().get(0).latitude(), res.places().get(0).longitude()};
        } catch (Exception ignore) { /* 키 없음·통신 실패 → CSV 로 */ }
        if (r.get("latitude") != null && r.get("longitude") != null) return new BigDecimal[]{new BigDecimal(r.get("latitude")), new BigDecimal(r.get("longitude"))};
        return null;
    }
    private void putElement(Long siteId, Long elementId, Map<String, String> r, String col) {
        if (SeedCsv.yes(r.get(col))) siteElementMapper.upsert(siteId, elementId, r.get(col + "_name"), null);   // 이름이 없어도 '있음' 은 기록(local_name null)
    }
    private static String describe(Map<String, String> r) {                            // ko description: 교구·구분·문화재를 한 줄로
        var sb = new StringBuilder();
        if (r.get("diocese") != null) sb.append(r.get("diocese"));
        if (r.get("note") != null) sb.append(sb.length() > 0 ? " · " : "").append(r.get("note"));
        return sb.length() == 0 ? null : sb.toString();
    }
    private static String seedKey(Map<String, String> s) { return s.get("region_code") + ":" + s.get("site_name") + ":" + Objects.toString(s.get("disambiguation"), ""); }
    private static int sortOf(List<Map<String, String>> all, Map<String, String> s) {     // 같은 구·같은 track 안에서 CSV 등장 순
        int n = 0; for (var x : all) { if (x.get("verse_no").equals(s.get("verse_no")) && x.get("track").equals(s.get("track"))) { n++; if (x == s) return n; } } return n;
    }
}
```
필요한 Mapper 추가: `SiteMapper.findIdBySeedKey`·`updateSeedKey`, `RegionMapper.findAll`, `CourseMapper.findIdByRegionAndName`, `CourseSiteMapper.findSlotIdsByCourse`(`@MapKey("position")`), `AdminSiteService.createCourseForSeed`(→ `AdminCourseService.create`). CSV 위치: `src/main/resources/seed/`.

> **site_element 컬럼 주의**: 시더가 `local_name = null`로 "있음"만 기록한 자리는 가는 법 응답에서 `localName null`로 나간다. 프론트 전달 목록에 "이름이 null이면 사전 이름(일주문 등)으로 표시"를 추가한다.

---

## 3-1. Q1 두 서비스 — 코드 규칙 (챕터 5·6에서 구현)

### 서비스 ① "이미 발행되었습니다" + 발행된 스탬프 보여주기
- `POST /api/stamps/{courseSiteId}/gps-check`에서 그 슬롯에 COMPLETED 도장이 있으면 **409 STAMP(이미 완료) + message "이미 발행된 스탬프입니다 — {사찰명} · {발행일}"**. 프론트가 바로 보여줄 수 있게 새 조회 하나:
```java
// StampController — 내 순례에서 이 슬롯의 완료 도장. 없으면 404
@GetMapping("/by-slot/{courseSiteId}")
public ApiResponse<StampStatusResponse> bySlot(@PathVariable @Positive Long courseSiteId, @AuthenticationPrincipal AuthenticatedUser user) {
    return ApiResponse.ok(stampService.findCompletedBySlot(user.userId(), courseSiteId));   // stamp JOIN pilgrimage WHERE user_id AND completed_course_site_id
}
```
- `StampStatusResponse`에 `siteId`·`siteName`(실제 인증 사찰)·`photoKey`·`userSentence` 4필드 추가(record 맨 뒤). 여권 칸(`SlotBlock`)에도 `siteName` 추가.
- 확인포인트: 슬롯 A에서 후보 X로 도장 → 후보 Y로 gps-check → **409 + message에 X 이름** → `GET /by-slot/{A}` 200 (siteId = X).

### 서비스 ② 사찰 1곳당 사진 1장 + 문장 1개 (도장 없이, 개인 소장 → 전자책)
- 표는 그대로: `photo(uk user_id+site_id)` = 사진 1장, `thinkbox(source='DIRECT', site_id)` = 문장 1개. **새 표 없음.**
- `PUT /api/photos/{siteId}` 본문에 `sentence`(선택, `@Size(max=300)` — thinkbox 상한과 동일) 추가. Service: photo UPSERT + 문장이 오면 `thinkbox` DIRECT 행을 `(user_id, site_id, source='DIRECT')` 기준 **UPSERT**(있으면 body 갱신·is_edited 규칙 적용). 도장 여부 검사 없음 — 후보든 아니든 등록된 사찰이면 허용(ACTIVE 사찰만).
- 전자책(챕터 9)은 `stamp.photo_key`·`user_sentence`(도장) + `photo`·`thinkbox DIRECT`(개인 소장) 둘 다 싣는다. `is_private=1`은 제외(기존 규칙).
- 확인포인트: 같은 사찰에 `PUT /api/photos` 2회 → `photo` 1행·`thinkbox` 1행 / 도장 없는 사찰도 200 / 문장 301자 → 400.

## 3-2. 조사된 사찰정보가 정확히 작동하는지 — 검증 규칙(STEP 4-검증에 포함)
1. **CSV ↔ DB 대조 테스트** `SeedConsistencyTest`(profile seed 후 실행): CSV의 `Y` 개수 = `site_element` 행 수(주불전·부속전각 포함), `main_hall_name` = element sort 6의 `local_name`, `flower_badge` 있는 사찰 = `site_badge FLOWER` 행. 하나라도 어긋나면 사찰명·열 이름을 출력하고 실패.
2. **가는 법 응답 대조**: 1차 조사 36곳 + 복귀 3곳에 대해 `GET /api/sites/{id}/guide` → `steps[].present`가 CSV Y/N과 같은지(`?`는 present=false). 조계사는 1~5 false·6·7 true, 화엄사는 7칸 true.
3. **좌표**: `latitude=0` 사찰 목록을 보고서에. 카카오 결과 카테고리에 "사찰"이 없는 건은 "재확인" 표기. 좌표 0인 사찰은 관리자 PATCH ACTIVE 시 `ADMIN-4092`(조건에 `latitude<>0` 추가).
4. **동명이찰**: `site.name` 중복(백련사 2·용문사 2·송광사 2·대원사 2·흥국사 2·천은사·부석사·용화사·선암사·화암사)이 서로 다른 `seed_key`·주소·권역을 갖는지 SQL로 확인해 보고서에 표.
5. **후보 무결성**: `slot_site` 모든 행이 course_site의 course.region과 site의 권역이 같은지(CSV region_code 기준), MAIN sort 1 = course_site.site_id(대표)인지.

## 4. Claude Code 지시문 — 확정본

```
루트 src/ 작업. git 없음. 원문 backend/docs/textbook/ch4-close.md + backend/docs/content/{sites-master.csv, slot-candidates.csv, research60.csv}. 스택 고정. 교재 정본. 챕터 5 는 시작하지 않는다.

STEP 0 — 결정 반영
 a. D1: frontend-handoff.md 에 "관리자 등록·수정 본문 status 금지(400 COMMON-4004) — PATCH /status 만" 추가.
 b. D3: SiteGuideService.toStep 을 ②안으로 — 각 자리가 자기 '오는 길' 한 문장씩(이어 붙이기 없음). 조계사(1~5 공백) 기대 문장을 테스트로 고정(7칸이 P1~P7 을 한 번씩).
 c. D2 는 코드 변경 없음 — ch5.md §1 에 "원칙① 유지 확정(D2)" 한 줄.

STEP 1 — 스키마·사전 확인(보고만). CSV 는 backend/docs/content/ 의 4개(sites-master 111행·slot-candidates 215행 — line_code 열로 코스 12개·research60·research70)가 정본이다. 묘각사 행은 시드에서 건너뛴다(note [Q2 확정]). is_congested 는 note 에 [Q3 확정] 이 있는 후보에 1: SELECT region_id, code, name FROM region ORDER BY sort_no / SELECT * FROM shrine_element / SHOW COLUMNS FROM site_element / SHOW COLUMNS FROM stamp. CSV 의 region_code 9개 ↔ DB region 매핑표를 만든다(이름 기준. DB 에 충남·충북이 없으면 [질문]). site_element 컬럼이 교재(local_name·note)와 다르면 교재 XML 을 실제 컬럼에 맞춘다.

STEP 2 — DDL: schema.sql 에 slot_site CREATE 추가, stamp.site_id·site.seed_key 를 CREATE 정의에 추가, 로컬 DB ALTER 2문 실행. deploy-checklist.md 에 ALTER 3건(stamp.expansion_phrase_id 포함) 기록.

STEP 3 — 코드: [기본 49]~[기본 51] 전부 + 필요한 Mapper 메서드. build.gradle 에 opencsv 5.9. CSV 두 개를 src/main/resources/seed/ 로 복사. CourseSiteResponse.candidates 추가 + CourseService 연결(RIDER 면 SUNROAD 우선). AdminSiteService.upsertElements/deleteElement/createCourseForSeed.

STEP 4 — 시드 실행: ./gradlew bootRun --args='--spring.profiles.active=local,seed' (KAKAO_REST_API_KEY 있음 → 좌표 실제 조회. 호출 106회, 1초 간격). 로그의 SEED DONE / no-coord / report 를 docs/audit/seed-v4.md 에 붙인다. 두 번 실행해 site·slot_site·site_element 행 수가 같은지(멱등) 확인.
 검증 SQL: site 110행(묘각사 제외), course 12행("○○ 공양의 길"), course_site 60행, slot_site 215행 안팎, site_element 는 조사값 있는 사찰분만, 좌표 0,0 사찰 목록. 라인별로 5구가 모두 후보를 갖는지(12 × 5 = 60 칸 전부) SQL 로 확인.

STEP 4' — 정확성 검증: ch4-close.md §3-2 의 1~5 를 SeedConsistencyTest + SQL 로 실행하고 결과 표를 docs/audit/seed-v4.md 에.

STEP 5 — 테스트·컬렉션: AdminSiteElement 테스트 3건(없는 코드 400 / 2회 UPSERT 1행 / guide 즉시 반영), 슬롯 후보 exists 1건, 코스 상세 candidates 1건. 컬렉션 S 폴더에 요소 API 2요청 append. run-all.sh 실패 0.

STEP 6 — 보고서 docs/audit/ch4-close.md: 매핑표 / 변경 파일 / 시드 결과 표(권역별 사찰 수·좌표 확보 수·요소 행 수·동명이찰 표) / §3-2 검증 결과 / [질문] (region 불일치·그 외). Q1~Q4 는 §0-1 로 확정됐으므로 다시 묻지 않는다. §0-2 A~D 는 예성 답 대기 — A~D 는 §0-1 로 확정(60 = 12코스 × 5). 회향 = ACTIVE 코스 전부 완주(목표 12). CompletionService 는 countActiveCourses() 를 그대로 쓴다 — 12 를 상수로 박으면 코스를 하나 내렸을 때 회향이 조용히 성립하지 않는다. 마지막 줄 "newman N/N, 기능 ✅ a · ⚠ b · ❌ c · — d". bootRun 종료.
```

**예성 직접**: ① `ch4-close.md` → `backend/docs/textbook/`, **CSV 4개 → `backend/docs/content/`** ② 위 지시문 붙여넣기 ③ 보고서 마지막 줄 확인. **챕터 5는 그 뒤 "시작"으로.**

---

## 5. 챕터 5(ch5.md)에 미리 반영할 변경 — Q1 답과 무관하게 확정

| 위치 | 변경 |
|---|---|
| `GpsCheckRequest` | `@NotNull @Positive Long siteId` 추가(후보 사찰). DTO 확정 79개 중 1개 변경 |
| `StampService.gpsCheck` | `slotSiteMapper.exists(courseSiteId, siteId)` 아니면 `COURSE_4001`; `insertGpsDone`에 `site_id` 저장; `qrLocationHint`·QR 사찰 검증(`qr.siteId == stamp.site_id`)·`site_distance`는 **stamp.site_id 기준** |
| `StampMapper.xml` | `insertGpsDone` 컬럼에 `site_id` · `findLastCompletedBefore` 반환에 `site_id` |
| 확인포인트 T04 | 본문 `{siteId: 대표 후보, withinRadius:true, accuracyGrade:"HIGH"}` · **T04b** 후보 아닌 siteId → 400 COURSE-4001 · **T11b** 도장 찍은 슬롯의 다른 후보 gps-check → 409 이미 완료, 그 사찰 `PUT /api/photos/{siteId}` 는 200(챕터 6) |
| §1 흐름도 | "슬롯(구) — 후보 중 하나에서 인증" 문구 |

준비 완료 조건: STEP 6 보고서 마지막 줄 ⚠ 0 + Q1~Q4 답.
