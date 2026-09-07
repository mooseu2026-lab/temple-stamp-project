// src/main/java/com/templestamp/admin/AdminCourseService.java
package com.templestamp.admin;

import com.templestamp.admin.dto.AdminCourseSaveRequest;
import com.templestamp.admin.dto.StatusChangeRequest;
import com.templestamp.course.Course;
import com.templestamp.course.CourseMapper;
import com.templestamp.course.CourseService;
import com.templestamp.course.CourseSiteMapper;
import com.templestamp.course.dto.CourseDetailResponse;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.global.error.ErrorResponse;
import com.templestamp.global.web.LocaleUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 코스 = 사찰 정확히 5곳, position 1~5 한 번씩, verseNo == position.
 * 사찰은 정확히 한 코스에만 속한다(uk_course_site_site) — 위반은 DB 도 막지만 여기서 미리 검사해
 * "중복 키" 대신 409 COURSE-4093 으로 바꿔 준다. 관리자가 어디를 고쳐야 할지 알 수 있게.
 * <p>
 * course_site 5행 교체는 한 트랜잭션 안에서 DELETE→INSERT 다. 문구·미션의 [RULE](UPSERT만)과 다른데,
 * 자리는 "비면 안 되는 콘텐츠" 가 아니라 "구성" 이기 때문이다. 게다가 진행 중 순례가 있으면
 * 교체 자체를 409 COURSE-4092 로 막으므로 중간 상태가 사용자에게 보이지 않는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminCourseService {

    /** 코스 하나는 사찰 5곳. 오관게 5구와 1:1 이라 늘어나지 않는다. */
    private static final int COURSE_SITE_COUNT = 5;

    private final CourseMapper courseMapper;
    private final CourseSiteMapper courseSiteMapper;
    private final CourseService courseService;   // 응답은 사용자용 CourseDetailResponse 를 재사용한다

    @Transactional
    public CourseDetailResponse create(AdminCourseSaveRequest req) {
        validateSlots(req, null);
        Long courseId = courseService.save(null, req);
        log.info("admin course created courseId={}", courseId);
        return detail(courseId);
    }

    @Transactional
    public CourseDetailResponse update(Long courseId, AdminCourseSaveRequest req) {
        requireCourse(courseId);
        if (courseMapper.countInProgressPilgrimages(courseId) > 0) {
            // 걷는 중에 자리를 바꾸면 이미 찍은 도장이 없는 자리를 가리키게 된다.
            throw new BusinessException(ErrorCode.COURSE_4092,
                    "진행 중인 순례가 있어 코스 구성을 바꿀 수 없습니다.");
        }
        validateSlots(req, courseId);
        courseService.save(courseId, req);
        return detail(courseId);
    }

    @Transactional
    public void changeStatus(Long courseId, StatusChangeRequest req) {
        requireCourse(courseId);

        if (Course.ACTIVE.equals(req.status())) {
            // 5곳이 전부 ACTIVE 사찰이어야 사용자에게 열 수 있다.
            // 하나라도 DRAFT 면 그 자리의 싱글페이지가 404 를 내고, 사용자는 코스 중간에서 막힌다.
            boolean ready = courseSiteMapper.countByCourseId(courseId) == COURSE_SITE_COUNT
                    && courseSiteMapper.countInactiveSites(courseId) == 0;
            if (!ready) {
                throw new BusinessException(ErrorCode.ADMIN_4092,
                        "사찰 5곳이 모두 공개(ACTIVE) 상태여야 코스를 공개할 수 있습니다.");
            }
        }

        courseMapper.updateStatus(courseId, req.status());
        log.info("admin course status courseId={} -> {}", courseId, req.status());
    }

    /* ---------------- 내부 ---------------- */

    /**
     * {@code @Valid} 가 못 잡는 세 조건.
     * <ul>
     *   <li>같은 자리(position)를 두 번 쓰지 않았는가 — COMMON-4000 + {@code fields[sites]}</li>
     *   <li>구절 번호가 자리 번호와 같은가 — COURSE-4001. 코스 5곳이 오관게 5구를 순서대로 맡는 구조다</li>
     *   <li>같은 사찰을 두 번 넣었거나, 다른 코스가 이미 가진 사찰인가 — COURSE-4093</li>
     * </ul>
     * 5곳·1~5 범위는 {@code @Size(min=5,max=5)}·{@code @Min/@Max} 가 이미 보장한다.
     * 중복이 없으면 여기까지 온 시점에 1~5 가 정확히 한 번씩이다.
     *
     * @param selfCourseId 수정 중인 코스. 자기 자신이 가진 사찰은 중복으로 보지 않는다.
     */
    private void validateSlots(AdminCourseSaveRequest req, Long selfCourseId) {
        Set<Integer> positions = new HashSet<>();
        Set<Long> sites = new HashSet<>();

        for (var slot : req.sites()) {
            if (!positions.add(slot.position())) {
                // COURSE-4042 는 "그 자리가 없다"(조회 실패) 는 뜻이다. 여기는 보낸 본문이 틀린 것이라
                // 검증 오류 형식으로 돌려준다 — 프론트가 sites 폼에 그대로 붙일 수 있게.
                throw new BusinessException(ErrorCode.COMMON_4000,
                        List.of(new ErrorResponse.FieldError("sites", "자리 번호가 겹칩니다.")));
            }
            if (!slot.verseNo().equals(slot.position())) {
                throw new BusinessException(ErrorCode.COURSE_4001);
            }
            if (!sites.add(slot.siteId())) {
                throw new BusinessException(ErrorCode.COURSE_4093, "같은 사찰이 두 번 들어갔습니다.");
            }
            Long owner = courseSiteMapper.findCourseIdBySite(slot.siteId()).orElse(null);
            if (owner != null && !owner.equals(selfCourseId)) {
                throw new BusinessException(ErrorCode.COURSE_4093);
            }
        }
    }

    private void requireCourse(Long courseId) {
        courseMapper.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_4041));
    }

    /** DRAFT 코스도 보여야 한다. findRowById 는 status 를 가리지 않으므로 그대로 쓴다. */
    private CourseDetailResponse detail(Long courseId) {
        return courseService.getCourseDetailAnyStatus(courseId, null, LocaleUtil.DEFAULT_LOCALE);
    }
}
