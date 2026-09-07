// src/main/java/com/templestamp/pilgrimage/PassportService.java
package com.templestamp.pilgrimage;

import com.templestamp.global.web.LocaleUtil;
import com.templestamp.manuscript.dto.ExtPhraseResponse;
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
    private final com.templestamp.user.UserMapper userMapper;

    @Transactional(readOnly = true)
    /**
     * @param localeHint 헤더에서 정해진 값. null 이면 users.locale → ko 로 이어 판단한다.
     *                   컨트롤러는 사용자를 조회하지 않으므로 이 자리는 Service 의 몫이다(챕터 2 규칙).
     */
    public PassportResponse build(Long userId, String localeHint) {
        String locale = localeHint != null ? localeHint : LocaleUtil.orDefault(userMapper.findLocale(userId));
        List<PassportSlotRow> rows = pilgrimageMapper.findPassportSlots(userId, locale);

        // 권역 id → (코스 id → 칸 목록). 두 겹 LinkedHashMap
        Map<Long, RegionAcc> regions = new LinkedHashMap<>();
        for (PassportSlotRow r : rows) {
            RegionAcc region = regions.computeIfAbsent(r.getRegionId(), k -> new RegionAcc(r.getRegionId(), r.getRegionName()));
            CourseAcc course = region.courses.computeIfAbsent(r.getCourseId(),
                    k -> new CourseAcc(r.getCourseId(), r.getCourseName(), r.getPilgrimageId(), r.getPilgrimageStatus()));
            boolean completed = r.getStampId() != null;                 // completed_course_site_id 조인이라 값이 있으면 곧 COMPLETED
            course.slots.add(new SlotBlock(r.getPosition(), r.getCourseSiteId(), r.getSiteName(), completed,
                    r.getStampId(), r.getCompletedAt(), r.getUserSentence(), r.getPhotoKey(),
                    ExtPhraseResponse.of(r.getExtTitle(), r.getExtBody(), r.getExtVariantNo())));
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
