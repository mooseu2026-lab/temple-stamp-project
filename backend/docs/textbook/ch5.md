# 챕터 5 — 현장 인증 3단계 (F-08 GPS → F-10 QR → F-09·F-11 미션·발급 · 명세 §5.5 · §6 상태 전이) ★ 핵심 챕터

> 작성일 2026-09-05 · 스택 고정: Spring Boot 3.5.16 · Java 21 · Lombok · MyBatis 3.0.5 · MySQL 8 · record · 담당 학생 B(stamp·QR)
> 파일 위치 `backend/docs/textbook/ch5.md`. **교재가 정본** — 저장소 stamp 코드는 검사(교체/삭제/유지) 후 교체, 원본은 `backend/docs/audit/ch5-replaced/`.
> 이 챕터의 첫 STEP은 코드가 아니라 **종단 테스트**다(의견91 수기 표 → 테스트).
> **교재 정정(2026-09-06, 챕터 5 실측)**: [기본 46·47] 만료 판정은 잠금 **앞**에서(`expireIfStale`). 잠근 뒤 REQUIRES_NEW 로 같은 행을 갱신하면 자기 교착 → 500.
> **v4 반영판(2026-09-06)**: gps-check 는 후보 사찰 `siteId` 를 받고, 이미 발행된 슬롯은 409 + 그 도장을 `GET /api/stamps/by-slot/{courseSiteId}` 로 보여준다(§3-1 Q1 ①). 확장문구 seen 기록이 여기(COMPLETED)로 온다. 문서 참조: frontend-handoff/deploy-checklist 는 `backend/docs/정리.md` §4/§5. 상태 전이 조건이 자바가 아니라 SQL `WHERE` 안에 있어 코드만 읽어서는 맞는지 알 수 없다.

---

## 0. 챕터 4 잔여 결정 (ch4-pilgrimage.md §8)

| # | 질의 | 결정 |
|---|---|---|
| 1 | 경쟁 재조회 `FOR UPDATE` | 반영 완료 |
| 2 | `GET /api/pilgrimages` 405 | 405 확정(정리.md §4-6) |
| 3 | 여권 locale Service 확정 | 동의 |
| 4 | **D2 원칙① 유지 확정** — 서버는 사용자 좌표를 받지 않는다 | 이 챕터 전체가 이 전제 위에 있다 |
| 5 | **v4 슬롯·후보 모델** — 코스 12·슬롯 60·후보 215 시드 완료 | gps-check 가 `siteId` 를 받는다(§5 delta 반영) |

---

## 1. 사용자 흐름 ↔ 프로그램 흐름 (명세 §5.5 · 흐름 4)

```
[사용자]  사찰 도착 → "도착 확인"        → 현장 QR 스캔             → 다짐 한 문장 + (사진)          → 도장 연출 / 보류 안내
             │                               │                           │                              │
[요청]   POST /api/stamps/{courseSiteId}/gps-check   POST /api/stamps/{stampId}/qr   POST /api/stamps/{stampId}/mission
         { siteId, withinRadius, accuracyGrade }     { qrToken }                     { sentence, photoKey?, expansionPhraseId? }
         (siteId = 이 슬롯의 후보 중 지금 서 있는 사찰. 후보가 아니면 400 COURSE-4001)
             │                               │                           │
[필터]   CoordinateFieldGuard — 좌표 필드가 실리면 400 COMMON-4001. 서버는 위치를 모른다(원칙①)
             │
[Service] ① 순례 ensureStarted → 후보 검증(slot_site.exists) → 일일 상한(5) → LOW 거부 → 반경 밖 거부 → 이미 완료면 409 "이미 발행되었습니다 — {사찰}·{날짜}" → stamp INSERT GPS_DONE(site_id 저장, 60분 카운트 시작)
          ② stamp 잠금(FOR UPDATE) → 만료면 REQUIRES_NEW 로 EXPIRED 기록 후 409 → 상태 GPS_DONE 아니면 순서 위반 → QR 서명·사찰·버전 검증 → 미션 배정 → QR_DONE
          ③ 잠금 → 만료/순서 검사 → 이동시간(site_distance) 미달이면 PENDING(TRAVEL_TIME) → 아니면 COMPLETED + seen 기록 + thinkbox + 보상 + 완주 재집계
             │
[DB]     stamp.completed_course_site_id(생성 컬럼) + UNIQUE → 완료 도장은 자리당 1개. 진행 중·만료·보류 행은 여러 개 허용
         잠금 순서 고정: pilgrimage → stamp → user_reward (규약 §7). 역순이면 관리자 승인과 교착
```

**예외 경로(F-11 EVIDENCE)**: GPS도 QR도 안 될 때 `POST /api/stamps/evidence { courseSiteId, sentence, photoKey(EVIDENCE/) }` → PENDING(EVIDENCE) → 관리자 심사(챕터 5 후반).

**서버가 못 믿는 것을 세 겹으로 메운다**(의견03 §6): QR(현장에 물리적으로 붙음, 버전 회전 가능) · 이동시간(site_distance) · 감사 점수(챕터 7, 지금은 기록만).

---

## 2. 상태 전이와 에러코드 — 이 표가 곧 SQL WHERE 절

| 전이 | 조건(SQL) | 실패 응답 |
|---|---|---|
| (없음) → GPS_DONE | 같은 pilgrimage·슬롯에 COMPLETED 없음 (`uk_stamp_completed`) + `slot_site.exists(슬롯, siteId)` | 이미 완료 **409**(메시지에 사찰·날짜) / 후보 아님 400 COURSE-4001 |
| GPS_DONE → QR_DONE | `verify_status='GPS_DONE' AND gps_verified_at >= NOW() - INTERVAL {session} MINUTE` | 순서 위반 409 / 만료 409(EXPIRED 기록 후) |
| QR_DONE → COMPLETED | `verify_status='QR_DONE' AND gps_verified_at >= …` + 이동시간 충족 | 순서 409 / 만료 409 / 미달 → **PENDING(TRAVEL_TIME)** (200) |
| QR_DONE → PENDING | 이동시간 미달 | — |
| (없음) → PENDING(EVIDENCE) | 하루 2건 이하 | 429 |
| PENDING → COMPLETED / REJECTED | 관리자 review | — |

의미 → 코드는 **저장소 `ErrorCode`의 STAMP 구역이 정본**이다(090501 §5에서 4101→409x, 422x→400 재번호 완료). Claude Code가 STEP 1에서 아래 의미 12개에 실제 코드를 대응시켜 보고서에 적는다: 반경 밖(400) · 정확도 LOW(400) · 남의 스탬프(403) · 이미 완료(409) · 순서 위반(409) · 세션 만료(409) · QR 무효/다른 사찰/구버전(400) · 하루 5개(429) · 예외접수 하루 2건(429) · 미션 없음(VERSE-4041) · 순례 없음(PILGRIM-4041) · 자리 없음(COURSE-4042). **새 코드는 만들지 않는다.**

---

## [기본 44] QrTokenProvider — 로그인 키와 분리된 서명

**파일** `src/main/java/com/templestamp/stamp/QrTokenProvider.java`
```java
// src/main/java/com/templestamp/stamp/QrTokenProvider.java
package com.templestamp.stamp;

import com.templestamp.global.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

/**
 * 현장 QR 토큰. 서명 알고리즘은 로그인과 같은 HS256 이지만 키는 app.qr.secret — 로그인 키가 새도 QR 을 위조하지 못한다.
 * 클레임: sub = siteId, ver = site.qr_version. 만료(exp) 없음 — 인쇄물이라 회전(ver+1)으로만 무효화한다.
 * 왜 URL 토큰이 아니라 서명 토큰인가: /checkin?placeId=3 은 숫자만 바꾸면 남의 사찰. 서명이 있으면 한 글자만 바꿔도 검증 실패(명세 §3).
 */
@Component
public class QrTokenProvider {

    private final SecretKey key;

    public QrTokenProvider(AppProperties props) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(props.qr().secret()));   // JWT_SECRET 과 반드시 다른 값. 같으면 RequiredEnvCheck 가 기동을 막는다
    }

    /** 관리자 발급용. site_id 와 현재 qr_version 을 박는다 */
    public String create(Long siteId, int qrVersion) {
        return Jwts.builder().subject(String.valueOf(siteId)).claim("ver", qrVersion).signWith(key).compact();
    }

    /** 서명이 유효하면 (siteId, ver). 위조·형식 오류는 JwtException — Service 가 "QR 무효" 로 바꾼다 */
    public QrPayload parse(String token) throws JwtException {
        Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return new QrPayload(Long.valueOf(c.getSubject()), c.get("ver", Integer.class));
    }

    public record QrPayload(Long siteId, int version) {}
}
```

**[담당: Claude Code]** — 저장소에 `QrTokenProviderTest` 7건이 이미 있다. 교재와 클레임 이름(`sub`·`ver`)이 다르면 **교재로 교체하고 테스트도 맞춘다**.

---

## [기본 45] StampMapper — 상태 전이를 SQL 이 지킨다

**파일** `src/main/resources/mapper/stamp/StampMapper.xml` (핵심 구문만 — 전체는 Claude Code 가 교재 규칙대로 완성)
```xml
<!-- 잠금 읽기: 2·3단계는 반드시 이걸로 시작. 잠금 순서 pilgrimage → stamp 를 지키기 위해 Service 가 pilgrimage 를 먼저 잠근다 -->
<select id="findByIdForUpdate" resultType="com.templestamp.stamp.Stamp">
  SELECT * FROM stamp WHERE stamp_id = #{stampId} FOR UPDATE
</select>

<!-- 1단계: 같은 자리에 완료 도장이 있으면 INSERT 자체가 uk_stamp_completed 에 안 걸린다(생성 컬럼은 COMPLETED 만 값이 있음) → Service 가 먼저 COUNT 로 막는다 -->
<select id="countCompletedAtSlot" resultType="int">
  SELECT COUNT(*) FROM stamp WHERE pilgrimage_id = #{pilgrimageId} AND completed_course_site_id = #{courseSiteId}
</select>
<insert id="insertGpsDone" useGeneratedKeys="true" keyProperty="stampId">
  INSERT INTO stamp (pilgrimage_id, course_site_id, site_id, verify_status, verify_method, accuracy_grade, gps_verified_at, expansion_phrase_id)
  VALUES (#{pilgrimageId}, #{courseSiteId}, #{siteId}, 'GPS_DONE', 'GPS_QR', #{accuracyGrade}, NOW(), NULL)
  <!-- site_id = 실제로 서 있는 후보 사찰(v4). QR 검증·이동시간 계산은 이 값을 쓴다 -->
</insert>

<!-- 2단계: WHERE 가 상태 전이 조건 전부다. 0행이면 Service 가 "왜 0 인가" 를 나눠 판단(만료/순서) -->
<update id="markQrDone">
  UPDATE stamp SET verify_status = 'QR_DONE', qr_verified_at = NOW(), mission_id = #{missionId}
   WHERE stamp_id = #{stampId} AND verify_status = 'GPS_DONE'
     AND gps_verified_at >= NOW() - INTERVAL #{sessionMinutes} MINUTE
</update>

<!-- 3단계 완료. ★ MySQL SET 은 왼쪽부터 평가 — 다른 컬럼을 읽는 대입이 있으면 읽는 쪽을 먼저(090501 §1 규칙). 여기선 없음 -->
<update id="markCompleted">
  UPDATE stamp SET verify_status = 'COMPLETED', mission_verified_at = NOW(),
         user_sentence = #{sentence}, photo_key = #{photoKey}, expansion_phrase_id = #{expansionPhraseId}
   WHERE stamp_id = #{stampId} AND verify_status = 'QR_DONE'
     AND gps_verified_at >= NOW() - INTERVAL #{sessionMinutes} MINUTE
</update>
<update id="markPendingTravelTime">
  UPDATE stamp SET verify_status = 'PENDING', pending_reason = 'TRAVEL_TIME', mission_verified_at = NOW(),
         user_sentence = #{sentence}, photo_key = #{photoKey}, expansion_phrase_id = #{expansionPhraseId}
   WHERE stamp_id = #{stampId} AND verify_status = 'QR_DONE'
</update>
<update id="markExpiredIfStale">
  UPDATE stamp SET verify_status = 'EXPIRED'
   WHERE stamp_id = #{stampId} AND verify_status IN ('GPS_DONE','QR_DONE')
     AND gps_verified_at &lt; NOW() - INTERVAL #{sessionMinutes} MINUTE
</update>
<update id="markAllStale">   <!-- 청소기용 -->
  UPDATE stamp SET verify_status = 'EXPIRED'
   WHERE verify_status IN ('GPS_DONE','QR_DONE') AND gps_verified_at &lt; NOW() - INTERVAL #{sessionMinutes} MINUTE
</update>

<!-- 이동시간 심사 재료: 이 순례의 직전 완료 도장 -->
<select id="findLastCompletedBefore" resultType="com.templestamp.stamp.Stamp">
  SELECT * FROM stamp WHERE pilgrimage_id = #{pilgrimageId} AND verify_status = 'COMPLETED' AND stamp_id &lt;&gt; #{stampId}
   ORDER BY mission_verified_at DESC LIMIT 1
</select>
<select id="countCompletedToday" resultType="int">
  SELECT COUNT(*) FROM stamp st JOIN pilgrimage p ON p.pilgrimage_id = st.pilgrimage_id
   WHERE p.user_id = #{userId} AND st.verify_status = 'COMPLETED' AND st.mission_verified_at >= CURDATE()
</select>
<!-- Q1 ①: 이 슬롯에서 내가 이미 받은 완료 도장 (by-slot 조회·409 메시지용) -->
<select id="findCompletedBySlot" resultType="com.templestamp.stamp.Stamp">
  SELECT st.* FROM stamp st JOIN pilgrimage p ON p.pilgrimage_id = st.pilgrimage_id
   WHERE p.user_id = #{userId} AND st.completed_course_site_id = #{courseSiteId}
</select>
<select id="countEvidenceToday" resultType="int">
  SELECT COUNT(*) FROM stamp st JOIN pilgrimage p ON p.pilgrimage_id = st.pilgrimage_id
   WHERE p.user_id = #{userId} AND st.verify_method = 'EVIDENCE' AND st.created_at >= CURDATE()
</select>
<insert id="insertEvidence" useGeneratedKeys="true" keyProperty="stampId">
  INSERT INTO stamp (pilgrimage_id, course_site_id, verify_status, verify_method, pending_reason, user_sentence, evidence_photo_key)
  VALUES (#{pilgrimageId}, #{courseSiteId}, 'PENDING', 'EVIDENCE', 'EVIDENCE', #{sentence}, #{evidencePhotoKey})
</insert>
```

**왜 UPDATE 의 WHERE 에 조건을 두는가** 자바에서 `if (status == GPS_DONE && !expired)` 로 검사한 뒤 UPDATE 하면, 검사와 UPDATE 사이에 다른 요청이 끼어든다. WHERE 에 두면 **검사와 갱신이 한 문장**이라 끼어들 틈이 없다. 대신 0행일 때 이유를 Service 가 다시 읽어 나눈다(아래 `explainZeroRows`).

**[담당: Claude Code]**

---

## [기본 46] StampExpireService — 예외보다 먼저 커밋되어야 하는 것

**파일** `src/main/java/com/templestamp/stamp/StampExpireService.java`
```java
// src/main/java/com/templestamp/stamp/StampExpireService.java
package com.templestamp.stamp;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 60분 만료 기록. 규약 §7: "예외를 던지기 전에 커밋되어야 하는 변경은 REQUIRES_NEW 로 분리한 별도 클래스에 둔다."
 * StampService 가 곧 BusinessException(만료)을 던져 자기 트랜잭션을 롤백하므로, 같은 클래스 안에서 EXPIRED 로 바꾸면 그것까지 롤백된다.
 * 별도 클래스여야 하는 이유: @Transactional 은 프록시라 같은 클래스 내부 호출(this.method())에는 안 걸린다.
 */
@Service
@RequiredArgsConstructor
public class StampExpireService {

    private final StampMapper stampMapper;

    /**
     * ★ 잠금 앞에서 부른다. 부모가 같은 행을 FOR UPDATE 로 잠근 뒤에 이걸 부르면, 새 트랜잭션이 부모의 잠금을 기다리다
     *   자기 자신과 교착한다(Lock wait timeout → 500). 챕터 5 실측(정리.md §8). 그래서 StampService 는 lockOwned() 이전에 expireIfStale() 을 호출한다.
     * 잠금 안에서 만료되는 좁은 창은 UPDATE 의 WHERE(gps_verified_at >= NOW()-60분)가 막고, 그 행은 5분 청소기가 닫는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)   // 부모 트랜잭션과 무관한 새 트랜잭션. 여기서 커밋되면 부모가 롤백돼도 남는다
    public boolean expireIfStale(Long stampId, int sessionMinutes) {
        return stampMapper.markExpiredIfStale(stampId, sessionMinutes) > 0;   // WHERE status IN (GPS_DONE,QR_DONE) AND gps_verified_at < NOW()-INTERVAL n MINUTE
    }

    /** 5분마다 도는 청소기(저장소 기존) — 돌아오지 않는 세션 정리. 교재 보완(2026-09-06) */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "PT5M")
    @Transactional
    public void sweep() { stampMapper.markAllStale(60); }
}
```

**[담당: Claude Code]** — 이 클래스는 챕터 1 `AuthService.login`의 `noRollbackFor`와 같은 문제를 다른 방법으로 푼 것이다. 둘의 차이를 교재 "왜"에 한 줄: `noRollbackFor`는 "이 예외에는 롤백하지 마라", `REQUIRES_NEW`는 "이 쓰기는 애초에 다른 트랜잭션이다". 만료 기록은 어떤 예외가 나든 남아야 하므로 후자.

---

## [기본 47] StampService — 3단계 전부

**파일** `src/main/java/com/templestamp/stamp/StampService.java`
```java
// src/main/java/com/templestamp/stamp/StampService.java
package com.templestamp.stamp;

import com.templestamp.course.CourseSiteMapper;
import com.templestamp.course.dto.ProgressResponse;
import com.templestamp.global.config.StampProperties;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.global.type.AccuracyGrade;
import com.templestamp.pilgrimage.Pilgrimage;
import com.templestamp.pilgrimage.PilgrimageMapper;
import com.templestamp.pilgrimage.PilgrimageService;
import com.templestamp.site.Site;
import com.templestamp.site.SiteDistanceMapper;
import com.templestamp.site.SiteMapper;
import com.templestamp.stamp.dto.*;
import com.templestamp.thinkbox.ThinkboxService;
import com.templestamp.user.UserMapper;
import com.templestamp.verse.PhraseService;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 현장 인증 3단계. 잠금 순서 pilgrimage → stamp → user_reward 고정(규약 §7).
 * 여기서 CompletionService·RewardService 는 호출만 한다 — 내부는 챕터 7 에서 교재화(저장소 기존 구현 유지·검사만).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StampService {

    private final StampMapper stampMapper;
    private final StampExpireService stampExpireService;
    private final PilgrimageMapper pilgrimageMapper;
    private final PilgrimageService pilgrimageService;
    private final CourseSiteMapper courseSiteMapper;
    private final SiteMapper siteMapper;
    private final SiteDistanceMapper siteDistanceMapper;
    private final com.templestamp.course.SlotSiteMapper slotSiteMapper;               // v4: 후보 검증
    private final UserMapper userMapper;
    private final PhraseService phraseService;
    private final QrTokenProvider qrTokenProvider;
    private final ThinkboxService thinkboxService;
    private final CompletionService completionService;           // 챕터 7
    private final StampProperties props;                          // sessionMinutes(60), defaultMinTravelMinutes

    // ── ① GPS ────────────────────────────────────────────────────────────────

    @Transactional
    public GpsCheckResponse gpsCheck(Long userId, Long courseSiteId, GpsCheckRequest req) {
        var slot = courseSiteMapper.findById(courseSiteId);                              // course_site 행: courseId·verseNo (대표 사찰은 참고용)
        if (slot == null) throw new BusinessException(ErrorCode.COURSE_4042);
        if (!slotSiteMapper.exists(courseSiteId, req.siteId())) throw new BusinessException(ErrorCode.COURSE_4001);   // v4: 이 슬롯의 후보가 아닌 사찰
        Pilgrimage p = pilgrimageService.ensureStarted(userId, slot.getCourseId());       // 코스 카드를 안 눌렀어도 도착 확인이 순례를 시작시킨다(멱등)
        pilgrimageMapper.findByIdForUpdate(p.getPilgrimageId());                          // ★ 잠금 1순위

        if (stampMapper.countCompletedToday(userId) >= 5) throw new BusinessException(ErrorCode.STAMP_4291);   // 하루 5개
        if (req.accuracyGrade() == AccuracyGrade.LOW) throw new BusinessException(ErrorCode.STAMP_4002);       // 재측위 요구
        if (!req.withinRadius()) throw new BusinessException(ErrorCode.STAMP_4001);                            // 단말이 판정한 "멈"
        Stamp done = stampMapper.findCompletedBySlot(userId, courseSiteId);
        if (done != null) {                                                               // Q1 ①: 이미 발행 — 어느 절에서 언제 받았는지 알려준다
            String name = siteMapper.findByIdAnyStatus(done.getSiteId()).getName();
            throw new BusinessException(ErrorCode.STAMP_4091, "이미 발행된 스탬프입니다 — " + name + " · " + done.getMissionVerifiedAt().toLocalDate());
            // ※ BusinessException 에 메시지 오버라이드 생성자가 없으면 STEP 2 에서 추가
        }

        Stamp s = new Stamp();
        s.setPilgrimageId(p.getPilgrimageId());
        s.setCourseSiteId(courseSiteId);
        s.setSiteId(req.siteId());                                                        // v4
        s.setAccuracyGrade(req.accuracyGrade().name());
        stampMapper.insertGpsDone(s);
        Site site = siteMapper.findActiveById(req.siteId());
        log.info("stamp gps_done stampId={} pilgrimageId={} slot={}", s.getStampId(), p.getPilgrimageId(), courseSiteId);
        return new GpsCheckResponse(s.getStampId(), "GPS_DONE", site.getQrLocationHint(), expiresAt(LocalDateTime.now()));
    }

    // ── ② QR ─────────────────────────────────────────────────────────────────

    @Transactional
    public StampStatusResponse qrVerify(Long userId, Long stampId, QrVerifyRequest req) {
        expireOrThrow(stampId);                                                           // ★ 잠금 앞에서 만료 판정 (자기 교착 방지)
        Stamp s = lockOwned(userId, stampId);                                             // pilgrimage 잠금 → stamp 잠금 → 소유 확인(403)
        var slot = courseSiteMapper.findById(s.getCourseSiteId());
        Site site = siteMapper.findActiveById(s.getSiteId());                            // v4: 1단계에서 서 있던 그 사찰의 QR 이어야 한다

        QrTokenProvider.QrPayload qr;
        try { qr = qrTokenProvider.parse(req.qrToken()); }
        catch (JwtException | IllegalArgumentException e) { throw new BusinessException(ErrorCode.STAMP_QR_INVALID); }   // 서명 위조·형식 오류
        if (!qr.siteId().equals(site.getSiteId())) throw new BusinessException(ErrorCode.STAMP_QR_INVALID);            // 다른 사찰의 QR
        if (qr.version() != site.getQrVersion()) throw new BusinessException(ErrorCode.STAMP_QR_INVALID);              // 회전으로 폐기된 구버전

        String tier = userMapper.findTier(userId);                                        // 토큰에 tier 가 없으므로 여기서 조회(명세 7.2)
        Long missionId = phraseService.pickMission(userId, slot.getCourseId(), slot.getVerseNo(), tier);   // 없으면 VERSE-4041 (미리보기와 다른 미션이 배정될 수 있음 — 미리보기는 미리보기)

        int n = stampMapper.markQrDone(stampId, missionId, props.sessionMinutes());
        if (n == 0) explainZeroRows(s);                                                   // 만료(EXPIRED 기록 후 409) 또는 순서 위반(409)
        return status(stampMapper.findById(stampId));
    }

    // ── ③ 미션 → 발급 ────────────────────────────────────────────────────────

    @Transactional
    public MissionResultResponse submitMission(Long userId, Long stampId, MissionSubmitRequest req) {
        expireOrThrow(stampId);                                                           // ★ 잠금 앞에서 만료 판정
        Stamp s = lockOwned(userId, stampId);
        Pilgrimage p = pilgrimageMapper.findById(s.getPilgrimageId());
        var slot = courseSiteMapper.findById(s.getCourseSiteId());
        String photoKey = (req.photoKey() == null || req.photoKey().isBlank()) ? null : req.photoKey();
        if (photoKey != null && !photoKey.startsWith("PHOTO/" + userId + "/")) throw new BusinessException(ErrorCode.COMMON_4000);   // 키 안의 userId 가 본인인지(DTO 주석)

        // expansionPhraseId(선택): 클라이언트가 /page 에서 본 문구. 이 구절·이 계층의 것인지 서버가 확인 후에만 기록(챕터 2 결정 셋째 안)
        Long phraseId = phraseService.validatePhraseFor(req.expansionPhraseId(), slot.getVerseNo(), userMapper.findTier(userId));   // 틀리면 null 로 (기록만 건너뜀)

        // 이동시간 심사: 직전 완료 도장 → site_distance(min_minutes) → 경과 시간 비교
        Stamp prev = stampMapper.findLastCompletedBefore(p.getPilgrimageId(), stampId);
        boolean tooFast = false;
        if (prev != null) {
            Long prevSiteId = prev.getSiteId();                                                 // v4: 직전 도장의 실제 사찰
            Integer min = siteDistanceMapper.findMinMinutes(prevSiteId, s.getSiteId());        // 없으면 props.defaultMinTravelMinutes
            int minMinutes = min != null ? min : props.defaultMinTravelMinutes();
            tooFast = Duration.between(prev.getMissionVerifiedAt(), LocalDateTime.now()).toMinutes() < minMinutes;
        }

        if (tooFast) {
            int n = stampMapper.markPendingTravelTime(stampId, req.sentence(), photoKey, phraseId);
            if (n == 0) explainZeroRows(s);
            log.info("stamp pending(travel) stampId={}", stampId);
            return new MissionResultResponse(stampId, "PENDING", "이동 시간을 확인하고 있습니다. 관리자 확인 후 순례 수첩에 반영됩니다.",
                    pilgrimageService.progressOf(userId, slot.getCourseId()), List.of(), false, null);   // 보류는 보상 없음
        }

        int n = stampMapper.markCompleted(stampId, req.sentence(), photoKey, phraseId, props.sessionMinutes());
        if (n == 0) explainZeroRows(s);
        // 완료 뒤에만 남기는 기록들 — 미리보기에서는 절대 안 남긴다(챕터 2 결정)
        if (phraseId != null) phraseService.markPhraseSeen(userId, slot.getCourseId(), phraseId);          // INSERT IGNORE
        phraseService.markTaskSeen(userId, slot.getCourseId(), s.getMissionId());                            // INSERT IGNORE
        thinkboxService.createFromMission(userId, stampId, req.sentence(), slot.getCourseId(), s.getSiteId());   // source=MISSION, uk_thinkbox_stamp

        CompletionResult cr = completionService.onStampCompleted(userId, p.getPilgrimageId(), stampId);   // 보상 적립 + 5개째면 COMPLETED·인증서·전자책 큐 (잠금 3순위 user_reward 는 이 안에서)
        log.info("stamp completed stampId={} courseCompleted={}", stampId, cr.courseCompleted());
        return new MissionResultResponse(stampId, "COMPLETED", "스탬프를 획득했습니다.",
                pilgrimageService.progressOf(userId, slot.getCourseId()), cr.rewards(), cr.courseCompleted(), cr.certificateSerial());
    }

    // ── 예외 접수 · 조회 ───────────────────────────────────────────────────────

    @Transactional
    public EvidenceResponse evidence(Long userId, EvidenceRequest req) {
        var slot = courseSiteMapper.findById(req.courseSiteId());
        if (slot == null) throw new BusinessException(ErrorCode.COURSE_4042);
        if (stampMapper.countEvidenceToday(userId) >= 2) throw new BusinessException(ErrorCode.STAMP_4292);
        if (!req.photoKey().startsWith("EVIDENCE/" + userId + "/")) throw new BusinessException(ErrorCode.COMMON_4000);
        Pilgrimage p = pilgrimageService.ensureStarted(userId, slot.getCourseId());
        if (stampMapper.countCompletedAtSlot(p.getPilgrimageId(), req.courseSiteId()) > 0) throw new BusinessException(ErrorCode.STAMP_4091);
        Stamp s = new Stamp();
        s.setPilgrimageId(p.getPilgrimageId()); s.setCourseSiteId(req.courseSiteId());
        stampMapper.insertEvidence(s.getPilgrimageId(), s.getCourseSiteId(), req.sentence(), req.photoKey(), s);
        return new EvidenceResponse(s.getStampId(), "PENDING", "관리자 확인 후 순례 수첩에 반영됩니다.", LocalDateTime.now());
    }

    /** Q1 ①: 이 슬롯에서 내가 받은 완료 도장. 없으면 404 */
    @Transactional(readOnly = true)
    public StampStatusResponse findCompletedBySlot(Long userId, Long courseSiteId) {
        Stamp s = stampMapper.findCompletedBySlot(userId, courseSiteId);
        if (s == null) throw new BusinessException(ErrorCode.COMMON_4040);
        return status(s);
    }

    @Transactional(readOnly = true)
    public StampStatusResponse getStatus(Long userId, Long stampId) {
        Stamp s = stampMapper.findById(stampId);
        if (s == null) throw new BusinessException(ErrorCode.COMMON_4040);
        if (!pilgrimageMapper.findById(s.getPilgrimageId()).getUserId().equals(userId)) throw new BusinessException(ErrorCode.STAMP_4031);
        return status(s);
    }

    // ── 내부 ─────────────────────────────────────────────────────────────────

    /** pilgrimage 잠금 → stamp 잠금 → 소유자 확인. 이 순서를 바꾸면 관리자 승인(같은 순서)과 교착 */
    private Stamp lockOwned(Long userId, Long stampId) {
        Stamp peek = stampMapper.findById(stampId);
        if (peek == null) throw new BusinessException(ErrorCode.COMMON_4040);
        Pilgrimage p = pilgrimageMapper.findByIdForUpdate(peek.getPilgrimageId());
        if (!p.getUserId().equals(userId)) throw new BusinessException(ErrorCode.STAMP_4031);   // 남의 스탬프 — 404 가 아니라 403(명세)
        return stampMapper.findByIdForUpdate(stampId);
    }

    /** 잠금 앞에서 호출. 60분이 지났으면 REQUIRES_NEW 로 EXPIRED 를 남긴 뒤 409 — 이 순서라야 자기 교착이 없다 */
    private void expireOrThrow(Long stampId) {
        if (stampExpireService.expireIfStale(stampId, props.sessionMinutes())) throw new BusinessException(ErrorCode.STAMP_EXPIRED);
    }

    /** UPDATE 가 0행일 때 — 만료는 위에서 이미 걸렀으므로 여기 오면 순서 위반이다(잠금 안에서 만료된 좁은 창은 청소기가 닫는다) */
    private void explainZeroRows(Stamp s) {
        throw new BusinessException(ErrorCode.STAMP_4092);                                // 순서 위반(예: GPS 없이 QR, 이미 COMPLETED)
    }

    private LocalDateTime expiresAt(LocalDateTime gpsAt) { return gpsAt.plusMinutes(props.sessionMinutes()); }

    private StampStatusResponse status(Stamp s) {
        Site site = s.getSiteId() == null ? null : siteMapper.findByIdAnyStatus(s.getSiteId());
        return new StampStatusResponse(s.getStampId(), s.getCourseSiteId(), s.getVerifyStatus(),
                s.getGpsVerifiedAt(), s.getQrVerifiedAt(), s.getMissionVerifiedAt(),
                s.getGpsVerifiedAt() == null ? null : expiresAt(s.getGpsVerifiedAt()),
                s.getSiteId(), site == null ? null : site.getName(), s.getPhotoKey(), s.getUserSentence());   // v4·Q1: 실제 사찰·사진·다짐 (record 맨 뒤 4필드 추가)
    }
}
```

`STAMP_QR_INVALID`·`STAMP_EXPIRED`·`STAMP_4091`은 **의미 이름**이다 — STEP 1 대응표의 실제 상수로 치환한다.

**DTO 변경 3건**: `GpsCheckRequest`에 `@NotNull @Positive Long siteId` 추가 · `MissionSubmitRequest`에 `@Positive Long expansionPhraseId`(선택) 추가 · `StampStatusResponse` 맨 뒤에 `siteId·siteName·photoKey·userSentence`. `PhraseService`에 `validatePhraseFor(phraseId, verseNo, tier)`(구절·계층 불일치면 null)·`markPhraseSeen`·`markTaskSeen` 추가. `SiteDistanceMapper.findMinMinutes(a, b)` 추가. `CourseSiteMapper.findById` 없으면 추가.

**[담당: Claude Code]**

---

## [기본 48] StampController + AdminStampController(QR 발급·회전·심사)

```java
// src/main/java/com/templestamp/stamp/StampController.java
package com.templestamp.stamp;

import com.templestamp.global.response.ApiResponse;
import com.templestamp.global.security.AuthenticatedUser;
import com.templestamp.stamp.dto.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** /api/stamps — 로그인 필요. 경로는 명세 §4 (gps-check 는 DTO 확정에 따라 courseSiteId 경로변수). 모든 POST 는 CoordinateFieldGuard 대상 */
@RestController
@RequestMapping("/api/stamps")
@RequiredArgsConstructor
@Validated
public class StampController {

    private final StampService stampService;

    @PostMapping("/{courseSiteId}/gps-check")
    public ApiResponse<GpsCheckResponse> gps(@PathVariable @Positive Long courseSiteId, @RequestBody @Valid GpsCheckRequest req,
                                             @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(stampService.gpsCheck(user.userId(), courseSiteId, req));
    }

    @PostMapping("/{stampId}/qr")
    public ApiResponse<StampStatusResponse> qr(@PathVariable @Positive Long stampId, @RequestBody @Valid QrVerifyRequest req,
                                               @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(stampService.qrVerify(user.userId(), stampId, req));
    }

    @PostMapping("/{stampId}/mission")
    public ApiResponse<MissionResultResponse> mission(@PathVariable @Positive Long stampId, @RequestBody @Valid MissionSubmitRequest req,
                                                      @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(stampService.submitMission(user.userId(), stampId, req));
    }

    @PostMapping("/evidence")
    public ApiResponse<EvidenceResponse> evidence(@RequestBody @Valid EvidenceRequest req, @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(stampService.evidence(user.userId(), req));
    }

    /** Q1 ①: 이미 발행된 슬롯의 내 도장 — 409 를 받은 프론트가 곧바로 보여준다 */
    @GetMapping("/by-slot/{courseSiteId}")
    public ApiResponse<StampStatusResponse> bySlot(@PathVariable @Positive Long courseSiteId, @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(stampService.findCompletedBySlot(user.userId(), courseSiteId));
    }

    @GetMapping("/{stampId}")
    public ApiResponse<StampStatusResponse> status(@PathVariable @Positive Long stampId, @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(stampService.getStatus(user.userId(), stampId));   // 앱 복귀용 — 60분 안에 어디까지 왔는지
    }
}
```

`AdminStampController` (`/api/admin/stamps` · `/api/admin/sites/{siteId}/qr`): `POST /qr` → `QrIssueResponse(courseSiteId 대신 siteId, qrToken, issuedAt, expiresAt null)` + zxing PNG를 `pngBase64` 필드로(DTO 1필드 추가) · `POST /qr/rotate` → `site.qr_version + 1` 후 재발급 · `GET /pending` → `PendingStampResponse` 목록(사진은 presign URL) · `POST /{stampId}/review {decision, reason}` → APPROVE: 잠금 순서 지켜 `COMPLETED` + `completionService.onStampCompleted` / REJECT: `REJECTED` + `review_note`(reason 필수는 Service). 코드는 [기본 47]과 같은 규칙으로 Claude Code가 작성하고 보고서에 전문을 붙인다.

**[담당: Claude Code]**

---

## 5. 확인포인트 (컬렉션 폴더 T — 로그인 사용자 + 관리자)

| # | 요청 | 기대 |
|---|---|---|
| T01 | 관리자 `POST /api/admin/sites/1/qr` | 200, `qrToken`, `pngBase64` 존재 → 환경변수 `qrSite1` |
| T02 | `POST /api/stamps/{cs1}/gps-check {withinRadius:true, accuracyGrade:"LOW"}` | 400 정확도 |
| T03 | `{withinRadius:false, accuracyGrade:"HIGH"}` | 400 반경 밖 |
| T04 | `{siteId: 대표 후보, withinRadius:true, accuracyGrade:"HIGH"}` | 200 GPS_DONE, `expiresAt` = 지금+60분, `qrLocationHint` → `stampId` |
| T04b | `{siteId: 다른 슬롯의 사찰, …}` | 400 COURSE-4001 |
| T05 | `POST /api/stamps/{stampId}/mission` (QR 건너뜀) | 409 순서 위반 |
| T06 | `POST .../qr {qrToken:"garbage"}` | 400 QR 무효 |
| T07 | 사찰 2의 QR 로 `.../qr` | 400 QR 무효(다른 사찰) |
| T08 | `.../qr {qrToken: qrSite1}` | 200 QR_DONE, `qrVerifiedAt` |
| T09 | `.../mission {sentence:"짧다"}` | 400 COMMON-4000 (10자 미만) |
| T10 | `.../mission {sentence:"오늘 받은 것들의 이름을 하나씩 불러본다.", expansionPhraseId: <M09의 id>}` | 200 COMPLETED, `progress.completedCount 1`, `rewards` 1건 이상, `courseCompleted false` |
| T11 | 같은 자리 `gps-check` 다시(다른 후보 siteId 로) | 409, message 에 T10 사찰명·날짜 |
| T11b | `GET /api/stamps/by-slot/{cs1}` | 200, siteId = T04 값, userSentence = T10 문장 |
| T12 | 자리 2 gps → QR(사찰 2) → mission **즉시** | 200 **PENDING**(이동시간 미달, site_distance 20분) `rewards []` |
| T13 | 다른 계정 토큰으로 `GET /api/stamps/{stampId}` | 403 |
| T14 | 관리자 `POST /api/admin/sites/1/qr/rotate` → 옛 `qrSite1`로 자리 3 QR | 400 QR 무효(구버전) |
| T15 | 관리자 `GET /api/admin/stamps/pending` | T12 건 포함 · `POST /{id}/review {decision:"APPROVE"}` → 200 → 여권 completedCount 2 |
| T16 | `POST /api/stamps/evidence` ×3 | 3번째 429 |
| — | SQL | `phrase_seen` 1행(T10) · `task_seen` 1행 · `thinkbox` source=MISSION 1행 · `stamp.expansion_phrase_id` = T10 값 |

**만료(60분) 경로**는 newman으로 못 기다린다 → 통합 테스트에서 `gps_verified_at`을 `UPDATE`로 61분 전으로 돌린 뒤 QR → 409 + DB `EXPIRED` 확인(REQUIRES_NEW 실측).

---

## 6. Claude Code 지시문 — 확정본

```
루트 src/ 작업. git 없음. 원문 backend/docs/textbook/ch5.md. 스택 고정. ErrorCode (status, code, message). 교재 정본 원칙(ch4 와 동일) — 원본은 backend/docs/audit/ch5-replaced/.

STEP 0 — 시작 전 확인: 정리.md §6 함정 5개(SET 순서·롤백·LEFT JOIN·스냅샷·검증 멱등)를 읽고 이 챕터 코드에서 각각 어디에 걸리는지 보고서 §0 에 한 줄씩. ch4-close 마감 상태(JUnit 108·newman 420)에서 시작한다. 문서 참조: frontend-handoff → 정리.md §4, deploy-checklist → 정리.md §5.

STEP 1 — 종단 테스트 먼저 (코드 수정 없음)
 a. 의견91.md 의 수기 확인 표(스탬프 3단계)를 StampFlowIntegrationTest 로 옮긴다: GPS→QR→미션 정상 / 순서 위반 / LOW / 반경 밖 / 다른 사찰 QR / 구버전 QR / 이동시간 PENDING / 세션 만료(gps_verified_at 을 61분 전으로 UPDATE 후 QR → 409, DB 가 EXPIRED — REQUIRES_NEW 실측) / 하루 5개 / 이미 완료 / 남의 스탬프 403 / 예외접수 2건. 지금 저장소 코드로 돌려 통과·실패를 표로 → backend/docs/audit/ch5-stamp.md §1. **실패한 것이 곧 저장소의 버그 목록**이다. 고치지 말고 적는다.
 b. ErrorCode STAMP 구역을 읽고 ch5.md §2 의 의미 12개 ↔ 실제 상수 대응표를 §1 에 붙인다. 없는 의미가 있으면 [질문]으로(새 코드 추가 금지).
 c. stamp·admin(스탬프·QR 부분)·verse(pickMission)·thinkbox(createFromMission) 파일 검사표(근거/차이/판정).

STEP 2 — 교체: [기본 44]~[기본 48] 를 교재 코드로. 의미 이름(STAMP_QR_INVALID·STAMP_EXPIRED·STAMP_4091)은 §1-b 대응표의 실제 상수로. MissionSubmitRequest.expansionPhraseId 추가, PhraseService.validatePhraseFor·markPhraseSeen·markTaskSeen 추가(챕터 2 에서 뺀 seen 기록이 여기로 옴), SiteDistanceMapper.findMinMinutes, QrIssueResponse.pngBase64(zxing). CompletionService·RewardService 는 호출부만 맞추고 내부는 손대지 않는다(챕터 7). 경로: /{courseSiteId}/gps-check · /{stampId}/qr · /{stampId}/mission · /evidence · GET /{stampId} · GET /by-slot/{courseSiteId}. 저장소의 qr-verify 경로는 제거. v4: GpsCheckRequest.siteId + slot_site.exists 검증, stamp.site_id 저장, QR·이동시간·thinkbox 는 stamp.site_id 기준. Q1 ①: 이미 발행 409 메시지에 사찰·날짜(BusinessException 메시지 오버라이드 생성자 없으면 추가).

STEP 3 — 테스트: STEP 1 의 StampFlowIntegrationTest 가 전부 통과할 때까지. 추가로 잠금 순서 테스트 1건(사용자 미션 제출과 관리자 APPROVE 를 동시에 — 교착 없이 둘 다 끝나야 함). 목표 총 100건 이상.

STEP 4 — 컬렉션 폴더 "T 스탬프 3단계" 를 ch5.md §5 T01~T16(+T04b·T11b) 으로 append(P 다음). 시드 코스는 전부 DRAFT 이므로 검증용으로 서울 코스 1개를 관리자 API 로 ACTIVE 전환(대표 사찰 5곳에 qrLocationHint 를 PATCH 로 채운 뒤) — cleanup.sql 이 끝나면 다시 DRAFT 로. 자리 id·후보 siteId 는 GET /api/courses/{id} 의 sites[].courseSiteId·candidates[] 에서 읽어 환경변수로. cleanup.sql 에 stamp·thinkbox·phrase_seen·task_seen·user_reward 정리 추가(FK 순서: user_reward → thinkbox → stamp → …). run-all.sh 2회 연속 동일값 확인.

STEP 5 — 보고서 docs/audit/ch5-stamp.md: §0 함정 대응 / §1 테스트 표(교체 전/후) / 대응표 / 검사표 / 교체·삭제·유지 / 테스트 건수 / newman / AdminStampController 전문 / [질문]. 정리.md §1 표의 stamp 를 "완료·종단 테스트 있음" 으로, §4 에 프론트 전달(gps-check siteId 필수·by-slot·409 메시지) 추가. 마지막 줄 형식 동일. bootRun 종료.
```

**예성 직접**: `ch5.md`(v4 반영판) → `backend/docs/textbook/` 덮어쓰기 → §6 붙여넣기 → 마지막 줄 확인. 이 챕터는 길다 — STEP 1 보고서(§1 표)가 먼저 나오면 그것만 먼저 올려주셔도 됩니다.

다음: **챕터 6·7 — 업로드 presign·사진 / 완주 재집계·인증서·보상(CompletionService·RewardService 교재화)**
