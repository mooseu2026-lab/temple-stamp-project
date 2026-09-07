package com.templestamp.course;

import com.templestamp.admin.dto.AdminCourseSaveRequest;
import com.templestamp.course.dto.CourseDetailResponse;
import com.templestamp.course.dto.CourseResponse;
import com.templestamp.course.dto.CourseRow;
import com.templestamp.course.dto.CourseSiteResponse;
import com.templestamp.course.dto.CandidateResponse;
import com.templestamp.course.dto.CourseSiteRow;
import com.templestamp.course.dto.SlotCandidateRow;
import com.templestamp.course.dto.ProgressResponse;
import com.templestamp.course.dto.RegionResponse;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.global.type.Tier;
import com.templestamp.pilgrimage.Pilgrimage;
import com.templestamp.pilgrimage.PilgrimageService;
import com.templestamp.stamp.StampMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseService {

    private final RegionMapper regionMapper;
    private final CourseMapper courseMapper;
    private final CourseSiteMapper courseSiteMapper;
    private final SlotSiteMapper slotSiteMapper;
    private final PilgrimageService pilgrimageService;
    private final StampMapper stampMapper;

    public List<RegionResponse> getRegions() {
        return regionMapper.findAll().stream()
                .map(RegionResponse::from)
                .toList();
    }

    /** userId 가 null(비로그인)이면 progress 를 채우지 않는다. */
    public List<CourseResponse> getCourses(Long regionId, Long userId) {
        if (regionId != null && regionMapper.existsById(regionId) == 0) {
            throw new BusinessException(ErrorCode.COURSE_4040);
        }
        return courseMapper.findActive(regionId).stream()
                .map(row -> CourseResponse.of(row,
                        pilgrimageService.progressOf(userId, row.getCourseId())))
                .toList();
    }

    /**
     * 공개 상세. ACTIVE 코스만 연다 — 목록에서 내린 코스가 직접 링크로 살아 있으면
     * "내렸다" 가 거짓이 된다. 관리자는 {@link #getCourseDetailAnyStatus} 를 쓴다.
     */
    public CourseDetailResponse getCourseDetail(Long courseId, Long userId, String locale) {
        return getCourseDetail(courseId, userId, locale, null);
    }

    /** target=RIDER 면 자리마다의 후보 목록에서 SUNROAD(선로드)를 앞으로 올린다. 내용은 같고 순서만 다르다. */
    public CourseDetailResponse getCourseDetail(Long courseId, Long userId, String locale, Tier target) {
        return buildDetail(courseMapper.findActiveRowById(courseId), courseId, userId, locale, target);
    }

    /** 관리자 응답용. status 를 가리지 않는다 — 등록 직후 DRAFT 코스를 그대로 돌려줘야 한다. */
    public CourseDetailResponse getCourseDetailAnyStatus(Long courseId, Long userId, String locale) {
        return buildDetail(courseMapper.findRowById(courseId), courseId, userId, locale, null);
    }

    private CourseDetailResponse buildDetail(java.util.Optional<CourseRow> found,
                                             Long courseId, Long userId, String locale, Tier target) {
        CourseRow row = found.orElseThrow(() -> new BusinessException(ErrorCode.COURSE_4041));

        Map<Long, List<CandidateResponse>> candidatesBySlot = candidatesOf(courseId, locale, target);
        List<CourseSiteResponse> sites = courseSiteMapper.findByCourseId(courseId, locale).stream()
                .map(site -> CourseSiteResponse.from(site, candidatesBySlot.get(site.getCourseSiteId())))
                .toList();

        return new CourseDetailResponse(
                row.getCourseId(), row.getRegionId(), row.getRegionName(),
                row.getName(), row.getDescription(),
                pilgrimageService.progressOf(userId, courseId),
                sites);
    }

    /**
     * 슬롯별 후보 목록. 한 번의 질의로 코스 전체를 읽어 자리마다 나눈다(자리당 질의 5번을 피한다).
     * <p>
     * <b>정렬</b> — 기본은 MAIN 먼저·같은 track 안에서는 sortNo 순이라 대표가 맨 앞에 온다.
     * {@code target=RIDER} 면 SUNROAD 를 앞으로 올린다. 라이더는 길(route_note)을 먼저 보고 고르기 때문이다.
     * 그 다음 과포화(congested)를 뒤로 민다 — 감추지는 않고 순서만 내린다.
     * 목록의 내용은 어느 쪽이든 같다.
     */
    private Map<Long, List<CandidateResponse>> candidatesOf(Long courseId, String locale, Tier target) {
        boolean sunroadFirst = target == Tier.RIDER;
        Comparator<SlotCandidateRow> order = Comparator
                .comparingInt((SlotCandidateRow r) -> trackRank(r.getTrack(), sunroadFirst))
                .thenComparing(r -> Boolean.TRUE.equals(r.getCongested()))   // false 가 먼저다
                .thenComparing(r -> r.getSortNo() == null ? Integer.MAX_VALUE : r.getSortNo());

        Map<Long, List<CandidateResponse>> bySlot = new LinkedHashMap<>();
        List<SlotCandidateRow> rows = new ArrayList<>(slotSiteMapper.findByCourse(courseId, locale));
        rows.sort(order);
        for (SlotCandidateRow r : rows) {
            bySlot.computeIfAbsent(r.getCourseSiteId(), k -> new ArrayList<>()).add(CandidateResponse.from(r));
        }
        return bySlot;
    }

    private static int trackRank(String track, boolean sunroadFirst) {
        boolean sunroad = "SUNROAD".equals(track);
        return sunroad == sunroadFirst ? 0 : 1;
    }

    // GET /api/courses/{id}/progress 는 명세 §4 에 없어 제거했다(ch4 부수 결정).
    // 진행률은 CourseResponse·CourseDetailResponse 와 챕터 5 MissionResultResponse.progress 로 실린다.
    // 계산은 PilgrimageService.progressOf 한 곳에서만 한다 — 출처가 둘이면 서로 어긋난다.

    /**
     * 사찰 ID 하나로 코스·자리·구절을 찾는다.
     * course_site.site_id 에 UNIQUE 가 걸려 있어 답이 하나로 정해진다 —
     * 싱글페이지와 GPS 인증이 siteId 만 받고도 동작하는 근거다.
     */
    public CourseSiteRow requireSlotBySite(Long siteId) {
        return courseSiteMapper.findBySiteId(siteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_4000,
                        "어느 코스에도 속하지 않은 사찰입니다."));
    }

    public CourseSiteRow requireSlot(Long courseSiteId) {
        return courseSiteMapper.findRowById(courseSiteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_4000));
    }

    public Course getCourse(Long courseId) {
        return courseMapper.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_4041));
    }

    /** 노출 중인 전체 코스 수. 여권 요약과 전 코스 완주 판정의 분모다. */
    public int countActiveCourses() {
        return courseMapper.countActive();
    }

    /** 노출 중인 코스에 속한 전체 자리 수. 여권 요약의 분모다. */
    public int countActiveSites() {
        return courseSiteMapper.countActiveSites();
    }

    /* ---------------- 관리자 ---------------- */

    /**
     * 코스 등록/수정. 연결된 사찰은 통째로 갈아 끼운다.
     * 부분 수정을 허용하면 position 이 중간에 비는 상태가 생겨 여권이 깨진다.
     */
    @Transactional
    public Long save(Long courseId, AdminCourseSaveRequest request) {
        if (regionMapper.existsById(request.regionId()) == 0) {
            throw new BusinessException(ErrorCode.COURSE_4040);
        }
        validateSlots(request.sites());

        Course course = Course.builder()
                .courseId(courseId)
                .regionId(request.regionId())
                .name(request.name())
                .description(request.description())
                .status(request.status() != null ? request.status() : Course.DRAFT)
                .sortNo(request.sortNo() == null ? 0 : request.sortNo())
                .build();

        if (courseId == null) {
            courseMapper.save(course);
        } else {
            getCourse(courseId);
            courseMapper.update(course);
        }

        List<CourseSite> slots = request.sites().stream()
                .map(slot -> CourseSite.builder()
                        .courseId(course.getCourseId())
                        .siteId(slot.siteId())
                        .position(slot.position())
                        .verseNo(slot.verseNo())
                        .build())
                .toList();

        courseSiteMapper.deleteByCourseId(course.getCourseId());
        courseSiteMapper.insertAll(slots);
        return course.getCourseId();
    }

    /**
     * position 1~5 와 verseNo 1~5 가 각각 한 번씩 쓰였는지, 같은 사찰이 두 번 들어가지 않았는지.
     * DB 유니크 제약이 잡아 주기는 하지만, 그러면 사용자에게 나가는 메시지가
     * "중복 키" 같은 것이 되어 어디를 고쳐야 할지 알 수 없다.
     */
    private void validateSlots(List<AdminCourseSaveRequest.SiteSlot> slots) {
        if (distinctCount(slots, AdminCourseSaveRequest.SiteSlot::position) != slots.size()) {
            throw new BusinessException(ErrorCode.COMMON_4000, "순번 1~5 가 겹칩니다.");
        }
        if (distinctCount(slots, AdminCourseSaveRequest.SiteSlot::verseNo) != slots.size()) {
            throw new BusinessException(ErrorCode.COMMON_4000, "구절 번호 1~5 가 겹칩니다.");
        }
        if (distinctCount(slots, AdminCourseSaveRequest.SiteSlot::siteId) != slots.size()) {
            throw new BusinessException(ErrorCode.COMMON_4000, "같은 사찰이 두 번 들어갔습니다.");
        }
    }

    private <T> long distinctCount(List<AdminCourseSaveRequest.SiteSlot> slots,
                                   java.util.function.Function<AdminCourseSaveRequest.SiteSlot, T> key) {
        return slots.stream().map(key).collect(Collectors.toSet()).size();
    }
}
