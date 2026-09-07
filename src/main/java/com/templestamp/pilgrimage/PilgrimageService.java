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

    /* ---------------- 교재 [기본 41] 에는 없지만 챕터 5 가 쓰는 것 ---------------- */

    /**
     * GPS 도착 확인이 부를 진입점. 순례를 아직 시작하지 않았어도 도장은 찍혀야 하므로
     * 없으면 만들고 있으면 그대로 준다. {@link #start} 와 같은 멱등 규칙을 쓴다.
     * <p>
     * StampService 가 쓴다. 여기서 코스 ACTIVE 검사를 다시 하지 않는 이유는,
     * 도장 경로가 이미 course_site → site(ACTIVE) 를 거쳐 들어오기 때문이다.
     */
    @Transactional
    public Pilgrimage ensureStarted(Long userId, Long courseId) {
        Pilgrimage existing = pilgrimageMapper.findByUserAndCourse(userId, courseId);
        if (existing != null) {
            return existing;
        }
        Pilgrimage p = new Pilgrimage();
        p.setUserId(userId);
        p.setCourseId(courseId);
        try {
            pilgrimageMapper.insert(p);
        } catch (DuplicateKeyException e) {
            return pilgrimageMapper.findByUserAndCourseForUpdate(userId, courseId);
        }
        return pilgrimageMapper.findById(p.getPilgrimageId());
    }

    /** 챕터 5 StampService 용. */
    @Transactional(readOnly = true)
    public Pilgrimage getById(Long pilgrimageId) {
        Pilgrimage p = pilgrimageMapper.findById(pilgrimageId);
        if (p == null) {
            throw new BusinessException(ErrorCode.PILGRIMAGE_4040);
        }
        return p;
    }

    /** 남의 순례에 도장을 찍지 못하게 한다. 챕터 5 StampService 용. */
    @Transactional(readOnly = true)
    public Pilgrimage getOwned(Long userId, Long pilgrimageId) {
        Pilgrimage p = getById(pilgrimageId);
        if (!p.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PILGRIMAGE_4030);
        }
        return p;
    }

    private PilgrimageResponse toResponse(Pilgrimage p, boolean created) {
        return new PilgrimageResponse(p.getPilgrimageId(), p.getCourseId(), p.getStatus(), p.getStartedAt(), created);
    }
}
