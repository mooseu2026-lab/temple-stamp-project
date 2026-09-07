# 챕터 4 — 순례 시작·여권 (F-13 · 명세 §4 "순례·여권" · 흐름 2) + 중간 점검 ⚠ 5건 수정

> 작성일 2026-09-05 · 스택 고정: Spring Boot 3.5.16 · Java 21 · Lombok · MyBatis 3.0.5 · MySQL 8 · record · 담당 학생 A(pilgrimage)
> 파일 위치 `backend/docs/textbook/ch4.md`. 중간 점검 결과: newman 343/349, ✅ 22 · ⚠ 5 · ❌ 0. ⚠ 5건의 원인 셋(Q1~Q3)과 Q4를 §0에서 결정하고 STEP 0으로 먼저 고친다.

---

## 0. 중간 점검 Q1~Q4 결정 (2026-09-05 확정)

| # | 질의 | 결정 | 근거 |
|---|---|---|---|
| Q1 | 5회째 실패가 401, 403은 6회째부터 | **5회째에 403 AUTH-4031.** `login()`에서 `recordLoginFail` 뒤 "이번 실패로 잠겼는가"를 한 번 더 보고 잠겼으면 4031 | 프론트는 403으로 잠금 화면을 띄운다. 사용자가 한 번 더 눌러야 보이는 건 명세 E-12("15분 뒤 다시 시도")의 의도가 아니다. 잠금 시점(5회)은 그대로. `five_failures_lock_the_account` 테스트를 "5회째 403"으로 바꾼다 |
| Q2 | 약관 POST 배열 / DELETE 객체 비대칭 | **POST 배열 유지, DELETE는 `DELETE /api/users/me/agreements/{agreementType}` 경로변수로.** 본문 없음 | 동의는 가입 직후 여러 개를 한 번에(배열이 자연스럽다). 철회는 한 건씩 하는 행위라 경로변수가 맞고, DELETE에 본문을 싣는 건 프록시·클라이언트 호환성이 나쁘다. 컬렉션 U05·U06은 배열로 수정 |
| Q3 | INACTIVE·DRAFT 코스·사찰이 공개 상세에서 200 | **공개 경로는 ACTIVE만.** `GET /api/courses/{id}` INACTIVE·DRAFT → 404 COURSE-4041, `GET /api/sites/{id}`·`/page` → 404 SITE-4040. 관리자 경로는 status 무관 유지 | 목록에서 내린 것이 링크로 살아 있으면 "내렸다"가 거짓이 된다. 명세 F-05 "사찰 비활성 404" 명문. 조회를 공개용(`findActive*`)/관리자용(`find*AnyStatus`) 둘로 나눈다 |
| Q4 | 사찰 DRAFT 되돌리기에 조건 없음 | **INACTIVE와 같은 조건 적용** — ACTIVE 코스에 배정돼 있으면 409 COURSE-4092 | 코스 ACTIVE 조건이 "5곳 전부 ACTIVE"인데 뒤에서 한 곳이 DRAFT로 빠지면 그 조건이 깨진다. 상태를 낮추는 전이(→DRAFT, →INACTIVE)는 같은 검사를 받는다 |

부수 결정: `GET /api/pilgrimages`(목록)는 명세 §4에 없고 여권이 같은 정보를 담으므로 **`[AUDIT]` 후 제거**. `GET /api/courses/{id}/progress`도 이 챕터에서 **제거**(챕터 2 결정). 여권 경로는 명세대로 **`GET /api/passport`**(저장소의 `/api/pilgrimages/passport`는 이동).

---

## 1. 사용자 흐름 ↔ 프로그램 흐름 (전체명세 흐름 2 · 5.7)

```
[사용자]  여권 화면(S-03) 열기            →   권역 카드 → 코스 카드 탭            →   "순례 시작"          →   여권에 칸 5개 생김
             │                                   │                                    │                         │
[요청]   GET /api/passport                 GET /api/courses/{id}(챕터2)          POST /api/pilgrimages     GET /api/passport
             │  (로그인 필요)                                                     { courseId }
             ▼
[Security]  /api/passport · /api/pilgrimages/** → authenticated (anyRequest). 토큰 없음 401 AUTH-4013
             ▼
[Service]  PilgrimageService.start — 코스 ACTIVE? → 이미 있으면 그것(created:false, 200) / 없으면 INSERT(created:true, 200)
           PassportService.build   — SQL 한 방(region→course→course_site→site LEFT JOIN pilgrimage·stamp) → Java 에서 권역→코스→칸 3단 조립
             ▼
[DB]      pilgrimage(uk user_id+course_id) — 연타해도 1건. stamp.completed_course_site_id(생성 컬럼) 로 "완료 도장" 만 LEFT JOIN
```

**핵심 두 줄** ① 완주 개수를 컬럼에 저장하지 않는다 — 항상 `stamp`를 COUNT 한다(스키마 주석). ② 여권의 요약 숫자 4개(완주 코스/전체 코스/완료 사찰/전체 사찰)도 저장값이 아니라 조회 결과를 센 값이다(PassportResponse 주석).

---

## [기본 40] Pilgrimage 도메인 + Mapper

**파일** `src/main/java/com/templestamp/pilgrimage/Pilgrimage.java`
```java
// src/main/java/com/templestamp/pilgrimage/Pilgrimage.java
package com.templestamp.pilgrimage;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** pilgrimage 테이블 1행. 완주 수 컬럼이 없다 — 항상 stamp COUNT (스키마 주석). status 는 IN_PROGRESS / COMPLETED 문자열 */
@Getter
@Setter
@NoArgsConstructor
public class Pilgrimage {
    private Long pilgrimageId;
    private Long userId;
    private Long courseId;
    private String status;              // DEFAULT 'IN_PROGRESS'. COMPLETED 전이는 챕터 7 CompletionService 가 한다
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;  // 완주 전 null
}
```

**파일** `src/main/java/com/templestamp/pilgrimage/PilgrimageMapper.java`
```java
// src/main/java/com/templestamp/pilgrimage/PilgrimageMapper.java
package com.templestamp.pilgrimage;

import com.templestamp.pilgrimage.dto.PassportSlotRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper                                                        // @MapperScan(annotationClass = Mapper.class) 이 이 표시를 보고 빈으로 만든다
public interface PilgrimageMapper {
    Pilgrimage findByUserAndCourse(@Param("userId") Long userId, @Param("courseId") Long courseId);   // uk 기준 1건 또는 null
    Pilgrimage findByUserAndCourseForUpdate(@Param("userId") Long userId, @Param("courseId") Long courseId);   // 경쟁 경로 전용 — SELECT ... FOR UPDATE (스냅샷이 아닌 최신 커밋본)
    Pilgrimage findById(@Param("pilgrimageId") Long pilgrimageId);
    int insert(Pilgrimage pilgrimage);                         // useGeneratedKeys → pilgrimageId. 동시 연타는 uk 가 막고 Service 가 재조회
    int countCompletedStamps(@Param("pilgrimageId") Long pilgrimageId);   // ProgressResponse.completedCount 의 유일한 출처
    List<PassportSlotRow> findPassportSlots(@Param("userId") Long userId, @Param("locale") String locale);   // 여권 전체 — ACTIVE 코스만
}
```

**파일** `src/main/resources/mapper/pilgrimage/PilgrimageMapper.xml`
```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.templestamp.pilgrimage.PilgrimageMapper">

  <select id="findByUserAndCourse" resultType="com.templestamp.pilgrimage.Pilgrimage">
    SELECT pilgrimage_id, user_id, course_id, status, started_at, completed_at
      FROM pilgrimage WHERE user_id = #{userId} AND course_id = #{courseId}
  </select>

  <select id="findByUserAndCourseForUpdate" resultType="com.templestamp.pilgrimage.Pilgrimage">
    SELECT pilgrimage_id, user_id, course_id, status, started_at, completed_at
      FROM pilgrimage WHERE user_id = #{userId} AND course_id = #{courseId} FOR UPDATE
    <!-- DuplicateKeyException 뒤에만 쓴다. REPEATABLE READ 스냅샷을 우회해 상대 트랜잭션이 커밋한 행을 읽기 위해 -->
  </select>

  <select id="findById" resultType="com.templestamp.pilgrimage.Pilgrimage">
    SELECT pilgrimage_id, user_id, course_id, status, started_at, completed_at FROM pilgrimage WHERE pilgrimage_id = #{pilgrimageId}
  </select>

  <insert id="insert" useGeneratedKeys="true" keyProperty="pilgrimageId">
    INSERT INTO pilgrimage (user_id, course_id) VALUES (#{userId}, #{courseId})
    <!-- status·started_at 은 DB DEFAULT. uk_pilgrimage(user_id, course_id) 위반은 DuplicateKeyException → Service 가 잡아 기존 행 재조회 -->
  </insert>

  <select id="countCompletedStamps" resultType="int">
    SELECT COUNT(*) FROM stamp WHERE pilgrimage_id = #{pilgrimageId} AND verify_status = 'COMPLETED'
  </select>

  <!-- 여권 한 방 조회. 권역→코스(ACTIVE)→자리→사찰, 여기에 "내 순례" 와 "완료 도장" 을 LEFT JOIN.
       ★ st 의 조인 키가 completed_course_site_id(생성 컬럼) — COMPLETED 인 행에만 값이 있어 진행 중·만료·보류 도장은 자동으로 빠진다.
       ★ p 의 조건(user_id)이 ON 에 있다 — WHERE 로 내리면 미시작 코스가 통째로 사라진다(가는 법 R1 과 같은 함정) -->
  <select id="findPassportSlots" resultType="com.templestamp.pilgrimage.dto.PassportSlotRow">
    SELECT r.region_id            AS regionId,
           r.name                 AS regionName,
           c.course_id            AS courseId,
           c.name                 AS courseName,
           p.pilgrimage_id        AS pilgrimageId,
           p.status               AS pilgrimageStatus,
           cs.position            AS position,
           cs.course_site_id      AS courseSiteId,
           COALESCE(si.name, s.name) AS siteName,
           st.stamp_id            AS stampId,
           st.mission_verified_at AS completedAt,
           st.user_sentence       AS userSentence,
           st.photo_key           AS photoKey
      FROM region r
      JOIN course      c  ON c.region_id  = r.region_id AND c.status = 'ACTIVE'
      JOIN course_site cs ON cs.course_id = c.course_id
      JOIN site        s  ON s.site_id    = cs.site_id
      LEFT JOIN site_i18n si ON si.site_id = s.site_id AND si.locale = #{locale}
      LEFT JOIN pilgrimage p ON p.course_id = c.course_id AND p.user_id = #{userId}
      LEFT JOIN stamp st ON st.pilgrimage_id = p.pilgrimage_id AND st.completed_course_site_id = cs.course_site_id
     ORDER BY r.sort_no, r.region_id, c.sort_no, c.course_id, cs.position
  </select>
</mapper>
```

**[담당: Claude Code]** · 확인포인트: `findPassportSlots` 행 수 = ACTIVE 코스 수 × 5 (시드 기준 5행)

---

## [기본 41] PilgrimageService — 멱등 시작

**파일** `src/main/java/com/templestamp/pilgrimage/PilgrimageService.java`
```java
// src/main/java/com/templestamp/pilgrimage/PilgrimageService.java
package com.templestamp.pilgrimage;

import com.templestamp.course.CourseMapper;
import com.templestamp.course.dto.ProgressResponse;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.pilgrimage.dto.PilgrimageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 순례 시작(F-04 흐름 2). 명세 §4: "POST /api/pilgrimages 는 멱등하다. 같은 코스를 두 번 눌러도 순례는 하나만 생기고 200 이 온다."
 * 그래서 규약의 "신규 생성 201" 예외 — 두 경우 모두 200, created 로 구분.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PilgrimageService {

    private final PilgrimageMapper pilgrimageMapper;
    private final CourseMapper courseMapper;

    @Transactional
    public PilgrimageResponse start(Long userId, Long courseId) {
        if (!courseMapper.existsActive(courseId)) {                     // DRAFT·INACTIVE·없음 전부 여기서 끊는다
            throw new BusinessException(ErrorCode.COURSE_4092);          // "비활성 코스" — 없는 코스도 사용자 입장에선 같다(존재 여부 비노출)
        }
        Pilgrimage existing = pilgrimageMapper.findByUserAndCourse(userId, courseId);
        if (existing != null) {
            return toResponse(existing, false);                          // 이미 걷는 중(또는 완주) → 그대로 돌려준다
        }
        Pilgrimage p = new Pilgrimage();
        p.setUserId(userId);
        p.setCourseId(courseId);
        try {
            pilgrimageMapper.insert(p);                                  // useGeneratedKeys → pilgrimageId
        } catch (DuplicateKeyException e) {
            // 두 요청이 동시에 findByUserAndCourse 를 null 로 보고 둘 다 INSERT 한 경우. uk 가 한쪽을 막는다 → 그 쪽은 기존 행을 다시 읽는다
            // ★ 평범한 재조회(findByUserAndCourse)는 안 된다 — MySQL 기본 격리수준 REPEATABLE READ 에서 이 트랜잭션은 첫 SELECT 시점의
            //   스냅샷을 계속 보므로 상대가 방금 커밋한 행이 "여전히 없음" 으로 나와 null → 500. FOR UPDATE(잠금 읽기)는 최신 커밋본을 읽는다.
            //   (ch4-pilgrimage.md §3 — concurrent_start_creates_one_row 테스트가 실측)
            log.debug("pilgrimage race userId={} courseId={}", userId, courseId);
            return toResponse(pilgrimageMapper.findByUserAndCourseForUpdate(userId, courseId), false);
        }
        log.info("pilgrimage started pilgrimageId={} userId={} courseId={}", p.getPilgrimageId(), userId, courseId);
        return toResponse(pilgrimageMapper.findById(p.getPilgrimageId()), true);   // DEFAULT 로 채워진 status·started_at 을 읽으려고 재조회
    }

    /** 코스 목록·상세·미션 결과가 공유하는 진행률. 없으면 null (비로그인·미시작) */
    @Transactional(readOnly = true)
    public ProgressResponse progressOf(Long userId, Long courseId) {
        if (userId == null) return null;
        Pilgrimage p = pilgrimageMapper.findByUserAndCourse(userId, courseId);
        if (p == null) return null;
        return ProgressResponse.of(p.getPilgrimageId(), pilgrimageMapper.countCompletedStamps(p.getPilgrimageId()), p.getStatus());
    }

    private PilgrimageResponse toResponse(Pilgrimage p, boolean created) {
        return new PilgrimageResponse(p.getPilgrimageId(), p.getCourseId(), p.getStatus(), p.getStartedAt(), created);
    }
}
```

`CourseMapper.xml` append:
```xml
<select id="existsActive" resultType="boolean">
  SELECT COUNT(*) > 0 FROM course WHERE course_id = #{courseId} AND status = 'ACTIVE'
</select>
```

**왜 DuplicateKeyException을 잡는가** `SELECT → INSERT` 사이에 다른 요청이 끼어들 수 있다(코스 카드 연타, 두 기기). "먼저 SELECT 했으니 안전하다"는 생각이 흔한 오답이고, 진짜 방어는 `uk_pilgrimage`다. 코드는 그 방어가 걸렸을 때 사용자에게 500 대신 정상 응답을 주는 뒷정리다.

**[담당: Claude Code]**

---

## [기본 42] PassportService — 3단 조립

**파일** `src/main/java/com/templestamp/pilgrimage/PassportService.java`
```java
// src/main/java/com/templestamp/pilgrimage/PassportService.java
package com.templestamp.pilgrimage;

import com.templestamp.pilgrimage.dto.PassportResponse;
import com.templestamp.pilgrimage.dto.PassportResponse.*;
import com.templestamp.pilgrimage.dto.PassportSlotRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 여권(F-13). Mapper 가 준 평평한 행(ACTIVE 코스 × 5칸)을 권역 → 코스 → 칸으로 접는다.
 * LinkedHashMap 을 쓰는 이유 — SQL 의 ORDER BY(권역 sort_no → 코스 sort_no → position) 를 그대로 보존하기 위해. HashMap 이면 순서가 깨진다.
 * 요약 4개는 전부 이 행들을 센 값이다(저장값 없음).
 */
@Service
@RequiredArgsConstructor
public class PassportService {

    private final PilgrimageMapper pilgrimageMapper;

    @Transactional(readOnly = true)
    public PassportResponse build(Long userId, String locale) {
        List<PassportSlotRow> rows = pilgrimageMapper.findPassportSlots(userId, locale);

        // 권역 id → (코스 id → 칸 목록). 두 겹 LinkedHashMap
        Map<Long, RegionAcc> regions = new LinkedHashMap<>();
        for (PassportSlotRow r : rows) {
            RegionAcc region = regions.computeIfAbsent(r.getRegionId(), k -> new RegionAcc(r.getRegionId(), r.getRegionName()));
            CourseAcc course = region.courses.computeIfAbsent(r.getCourseId(),
                    k -> new CourseAcc(r.getCourseId(), r.getCourseName(), r.getPilgrimageId(), r.getPilgrimageStatus()));
            boolean completed = r.getStampId() != null;                 // completed_course_site_id 조인이라 값이 있으면 곧 COMPLETED
            course.slots.add(new SlotBlock(r.getPosition(), r.getCourseSiteId(), r.getSiteName(), completed,
                    r.getStampId(), r.getCompletedAt(), r.getUserSentence(), r.getPhotoKey()));
        }

        int completedCourses = 0, totalCourses = 0, completedSites = 0, totalSites = 0;
        List<RegionBlock> regionBlocks = new ArrayList<>();
        for (RegionAcc ra : regions.values()) {
            List<CourseBlock> courseBlocks = new ArrayList<>();
            for (CourseAcc ca : ra.courses.values()) {
                int done = (int) ca.slots.stream().filter(SlotBlock::completed).count();
                totalCourses++;
                totalSites += ca.slots.size();
                completedSites += done;
                if ("COMPLETED".equals(ca.status)) completedCourses++;
                courseBlocks.add(new CourseBlock(ca.courseId, ca.name, ca.pilgrimageId, ca.status, done, ca.slots));
            }
            regionBlocks.add(new RegionBlock(ra.regionId, ra.name, courseBlocks));
        }
        return new PassportResponse(new Summary(completedCourses, totalCourses, completedSites, totalSites), regionBlocks);
    }

    // 조립 중간 그릇. 밖으로 안 나가므로 record 가 아니라 가변 클래스 — slots 에 add 해야 하니까
    private static class RegionAcc {
        final Long regionId; final String name; final Map<Long, CourseAcc> courses = new LinkedHashMap<>();
        RegionAcc(Long regionId, String name) { this.regionId = regionId; this.name = name; }
    }
    private static class CourseAcc {
        final Long courseId; final String name; final Long pilgrimageId; final String status; final List<SlotBlock> slots = new ArrayList<>();
        CourseAcc(Long courseId, String name, Long pilgrimageId, String status) { this.courseId = courseId; this.name = name; this.pilgrimageId = pilgrimageId; this.status = status; }
    }
}
```

**자주 만나는 오류** 미시작 코스가 여권에서 통째로 사라진다 → XML의 `p.user_id = #{userId}`가 WHERE로 내려갔다. ON으로. / 완료 칸이 진행 중 도장까지 세어진다 → `st.completed_course_site_id`가 아니라 `st.course_site_id`로 조인했다.

**[담당: Claude Code]**

---

## [기본 43] Controller 2개 + 제거 2개 + 챕터 2 Service 연결

**파일** `src/main/java/com/templestamp/pilgrimage/PilgrimageController.java`
```java
// src/main/java/com/templestamp/pilgrimage/PilgrimageController.java
package com.templestamp.pilgrimage;

import com.templestamp.global.response.ApiResponse;
import com.templestamp.global.security.AuthenticatedUser;
import com.templestamp.pilgrimage.dto.PilgrimageResponse;
import com.templestamp.pilgrimage.dto.PilgrimageStartRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** POST /api/pilgrimages — 로그인 필요(anyRequest). 200 고정(멱등, 명세 §4). CoordinateFieldGuard 검사 대상 — 좌표 실리면 400 COMMON-4001 */
@RestController
@RequestMapping("/api/pilgrimages")
@RequiredArgsConstructor
@Slf4j
public class PilgrimageController {

    private final PilgrimageService pilgrimageService;

    @PostMapping
    public ApiResponse<PilgrimageResponse> start(@RequestBody @Valid PilgrimageStartRequest req,
                                                 @AuthenticationPrincipal AuthenticatedUser user) {   // 보호 경로라 null 이 아니다
        log.debug("POST /api/pilgrimages userId={} courseId={}", user.userId(), req.courseId());
        return ApiResponse.ok(pilgrimageService.start(user.userId(), req.courseId()));
    }
    // GET /api/pilgrimages(목록)는 명세 §4 에 없다 — 여권이 같은 정보를 담는다. 저장소의 것은 [AUDIT] 후 제거
}
```

**파일** `src/main/java/com/templestamp/pilgrimage/PassportController.java`
```java
// src/main/java/com/templestamp/pilgrimage/PassportController.java
package com.templestamp.pilgrimage;

import com.templestamp.global.response.ApiResponse;
import com.templestamp.global.security.AuthenticatedUser;
import com.templestamp.global.web.LocaleUtil;
import com.templestamp.pilgrimage.dto.PassportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** GET /api/passport — 명세 §4·§10(S-02 홈, S-03 여권). 저장소의 /api/pilgrimages/passport 는 이 경로로 이동. 캐시 60초는 프론트 몫 */
@RestController
@RequestMapping("/api/passport")
@RequiredArgsConstructor
public class PassportController {

    private final PassportService passportService;

    @GetMapping
    public ApiResponse<PassportResponse> passport(@RequestHeader(value = "Accept-Language", required = false) String acceptLanguage,
                                                  @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(passportService.build(user.userId(), LocaleUtil.resolve(acceptLanguage)));
        // 여권은 사용자 맥락이 있으니 LocaleUtil.hint + users.locale 이 원칙이나, 챕터 2 규칙상 "힌트 없으면 users.locale" 은 SitePageService 만 구현돼 있다.
        // → Claude Code: UserMapper.findLocale 을 써서 hint(null, header) → users.locale → ko 로 맞춘다(§5 STEP 3)
    }
}
```

**챕터 2 연결**: `CourseService.getCourses`·`getCourseDetail`의 progress 계산이 자체 SQL이면 `PilgrimageService.progressOf(userId, courseId)`로 교체(진행률 출처를 하나로). `CourseController.progress` 메서드·전용 Mapper 삭제.

**Q3 적용**: `CourseMapper.findRowById` → 공개용 `findActiveRowById`(status='ACTIVE') 신설, `CourseService.getCourseDetail`은 그것을 쓰고 없으면 `COURSE_4041`. 관리자 `AdminCourseService`는 기존 `findByIdAnyStatus`. `SiteMapper.findById` 공개 호출부(`SiteService.get`·`SitePageService`·`SiteGuideService`) → `findActiveById`(status='ACTIVE') / 관리자는 기존 유지.

---

## 5. 확인포인트 (컬렉션 폴더 P 추가 — 파일은 Claude Code가 `temple-stamp-all` 에 append)

| # | 요청 | 기대 |
|---|---|---|
| P01 | 비로그인 `GET /api/passport` | 401 AUTH-4013 |
| P02 | 로그인 `GET /api/passport` (미시작) | 200, `summary.totalCourses` = ACTIVE 코스 수, `completedSites 0`, 코스 `pilgrimageId null`, slots 5 |
| P03 | `POST /api/pilgrimages {courseId:1}` | 200, `created true`, status IN_PROGRESS |
| P04 | 같은 요청 재전송 | 200, `created false`, 같은 pilgrimageId |
| P05 | `POST /api/pilgrimages {courseId:999}` | 409 COURSE-4092 |
| P06 | `POST /api/pilgrimages {courseId:1, latitude:37.5}` | 400 COMMON-4001 |
| P07 | `GET /api/passport` 다시 | 코스 1 `pilgrimageId` 채워짐, status IN_PROGRESS, completedCount 0 |
| P08 | `GET /api/courses?regionId=1` 로그인 | `progress.pilgrimageId` = P03 값, `completedCount 0`, `total 5` |
| P09 | `GET /api/courses/1/progress` | 404 COMMON-4040 (제거됨) |
| P10 | `GET /api/pilgrimages/passport` | 404 (이동됨) |
| — | 재점검 A07x·U05~U07·C06 | 5회째 403 / 배열 본문 200 / INACTIVE 공개 404 |

SQL: `SELECT COUNT(*) FROM pilgrimage WHERE user_id=? AND course_id=1` → 1 (P03·P04 후)

---

## 6. Claude Code 지시문 — 확정본

```
루트 src/ 작업. git 없음. 원문 backend/docs/textbook/ch4.md. 스택 고정. ErrorCode (status, code, message). append 우선.

STEP 0 — 중간 점검 ⚠ 5건 수정 (ch4.md §0 확정)
 a. Q1: AuthService.login — recordLoginFail 뒤 잠금 여부를 재조회해 잠겼으면 AUTH_4031 을 던진다(noRollbackFor 유지 — 카운트 증가가 롤백되면 안 됨). AuthFlowIntegrationTest.five_failures_lock_the_account 를 "1~4회 401, 5회째 403" 으로 수정. 컬렉션 A07x 는 그대로.
 b. Q2: UserController DELETE /api/users/me/agreements → DELETE /api/users/me/agreements/{agreementType} (경로변수, 본문 없음). POST 는 배열 유지. 컬렉션 U05·U06 본문을 배열 [{...}] 로 수정. frontend-handoff.md 에 두 줄 추가(POST 배열 / DELETE 경로변수).
 c. Q3: 공개 조회를 ACTIVE 로 제한 — CourseMapper.findActiveRowById 신설 → CourseService.getCourseDetail 사용(없으면 COURSE_4041). SiteMapper.findActiveById 신설 → SiteService.get·SitePageService·SiteGuideService 사용(없으면 SITE_4040). 관리자 Service 는 기존 AnyStatus 유지. 테스트 2건: INACTIVE 코스 상세 404 / DRAFT 사찰 page 404.
 d. Q4: AdminSiteService.changeStatus — "DRAFT" 분기도 countActiveCourseAssignments > 0 이면 COURSE_4092. 테스트 1건.
 e. ./gradlew test 전부 통과 확인 후 STEP 1.

STEP 1 — 감사: pilgrimage 패키지·mapper/pilgrimage 전수를 ch4.md 와 대조. GET /api/pilgrimages(목록)·/api/pilgrimages/passport 에 [AUDIT] → 이 챕터에서 제거·이동. → backend/docs/audit/ch4-pilgrimage.md

STEP 2 — [기본 40]~[기본 42]: Pilgrimage·PilgrimageMapper·XML·PilgrimageService·PassportService. 저장소에 이미 있으면 diff 를 보고서에 붙이고 교재 규칙(멱등 200·DuplicateKeyException 재조회·existsActive·completed_course_site_id 조인·ON 조건·요약 4개 계산값)이 빠진 곳만 채운다. CourseMapper.existsActive append.

STEP 3 — [기본 43]: PilgrimageController(POST 만)·PassportController(GET /api/passport, locale 은 LocaleUtil.hint(null, header) → UserMapper.findLocale → ko). GET /api/pilgrimages 목록·/api/pilgrimages/passport·GET /api/courses/{id}/progress 및 전용 Service/Mapper 메서드 삭제. CourseService 의 progress 계산을 PilgrimageService.progressOf 로 통일.

STEP 4 — 테스트: PilgrimageIntegrationTest 신규 — 시작 created:true / 재요청 created:false 같은 id / 비활성 코스 409 / 여권 미시작 slots 5·pilgrimageId null / 시작 후 pilgrimageId 채워짐 / 동시 2회 시작(ExecutorService 2스레드) 후 행 1건. 목표 총 76건 이상.

STEP 5 — 컬렉션: temple-stamp-all 에 폴더 "P 순례·여권" 을 ch4.md §5 P01~P10 으로 append(폴더 U 다음, M 앞). 재실행 bash postman/run-all.sh → 이전 실패 6건이 0이 되어야 한다. SQL 보조 all-checkpoints.sql 에 pilgrimage 1건 확인 1줄 append.

STEP 6 — 보고서 backend/docs/audit/ch4-pilgrimage.md: STEP 0 변경 파일 / 삭제한 엔드포인트 3개 / 테스트 건수 / newman 결과(실패 0 기대) / [질문]. feature-status-ch0-3.md 의 ⚠ 5행을 ✅ 로 갱신하고 F-13 여권 행을 추가. 마지막 줄: "newman N/N, 기능 ✅ a · ⚠ b · ❌ c · — d". bootRun 종료.
```

**예성 직접**: 이 파일 → `backend/docs/textbook/ch4.md` 저장 → §6 붙여넣기 → `ch4-pilgrimage.md` 마지막 줄 확인(⚠·❌ 0이면 끝).

다음: **챕터 5 — 스탬프 3단계 인증** (첫 STEP은 의견91 수기 표 → 종단 테스트 이식, 그 다음 `expansionPhraseId` 기록·QR 발급·StampExpireService REQUIRES_NEW)
