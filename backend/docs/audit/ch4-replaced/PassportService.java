package com.templestamp.pilgrimage;

import com.templestamp.course.CourseService;
import com.templestamp.pilgrimage.dto.PassportResponse;
import com.templestamp.pilgrimage.dto.PassportSlotRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 여권 조립. 평평한 슬롯 목록을 권역 → 코스 → 칸 3단으로 접는다.
 * <p>
 * 조회는 한 방이다. 권역·코스·칸을 각각 부르면 코스 10개짜리 여권에서 쿼리가 수십 번 나간다.
 * 대신 정렬(권역 → 코스 → 칸)을 SQL 이 책임지고, 여기서는 순서를 유지하는 LinkedHashMap 으로
 * 묶기만 한다.
 * <p>
 * 상단 요약 수치는 전부 계산값이다. 어디에도 저장하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PassportService {

    private final PilgrimageMapper pilgrimageMapper;
    private final CourseService courseService;

    public PassportResponse getPassport(Long userId) {
        List<PassportSlotRow> rows = pilgrimageMapper.findPassportSlots(userId);

        Map<Long, RegionAccumulator> regions = new LinkedHashMap<>();
        for (PassportSlotRow row : rows) {
            regions.computeIfAbsent(row.getRegionId(),
                            id -> new RegionAccumulator(id, row.getRegionName()))
                    .add(row);
        }

        List<PassportResponse.RegionBlock> regionBlocks = regions.values().stream()
                .map(RegionAccumulator::toBlock)
                .toList();

        return new PassportResponse(summarize(userId), regionBlocks);
    }

    private PassportResponse.Summary summarize(Long userId) {
        return new PassportResponse.Summary(
                pilgrimageMapper.countCompletedCourses(userId),
                courseService.countActiveCourses(),
                pilgrimageMapper.countCompletedStamps(userId),
                courseService.countActiveSites());
    }

    /* ---------------- 접기 위한 임시 누적기 ---------------- */

    private static final class RegionAccumulator {

        private final Long regionId;
        private final String regionName;
        private final Map<Long, CourseAccumulator> courses = new LinkedHashMap<>();

        private RegionAccumulator(Long regionId, String regionName) {
            this.regionId = regionId;
            this.regionName = regionName;
        }

        private void add(PassportSlotRow row) {
            courses.computeIfAbsent(row.getCourseId(), id -> new CourseAccumulator(row)).add(row);
        }

        private PassportResponse.RegionBlock toBlock() {
            return new PassportResponse.RegionBlock(regionId, regionName,
                    courses.values().stream().map(CourseAccumulator::toBlock).toList());
        }
    }

    private static final class CourseAccumulator {

        private final Long courseId;
        private final String courseName;
        private final Long pilgrimageId;
        private final String status;
        private final List<PassportResponse.SlotBlock> slots = new ArrayList<>();
        private int completed;

        private CourseAccumulator(PassportSlotRow row) {
            this.courseId = row.getCourseId();
            this.courseName = row.getCourseName();
            this.pilgrimageId = row.getPilgrimageId();
            this.status = row.getPilgrimageStatus();
        }

        private void add(PassportSlotRow row) {
            boolean stamped = row.getStampId() != null;
            slots.add(new PassportResponse.SlotBlock(
                    row.getPosition(), row.getCourseSiteId(), row.getSiteName(), stamped,
                    row.getStampId(), row.getCompletedAt(), row.getUserSentence(), row.getPhotoKey()));
            if (stamped) {
                completed++;
            }
        }

        private PassportResponse.CourseBlock toBlock() {
            return new PassportResponse.CourseBlock(
                    courseId, courseName, pilgrimageId, status, completed, slots);
        }
    }
}
