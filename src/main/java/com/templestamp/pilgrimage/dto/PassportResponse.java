package com.templestamp.pilgrimage.dto;

import com.templestamp.manuscript.dto.ExtPhraseResponse;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 여권 화면 전체를 권역 → 코스 → 칸 순서로 담아 내려주는 DTO Record입니다.
 * 상단 요약 수치는 모두 조회 결과를 센 값입니다(저장된 값이 아님).
 *   completedCourses = COUNT(pilgrimage) WHERE status='COMPLETED'
 *   totalCourses     = COUNT(course) WHERE status='ACTIVE'
 *   completedSites   = COUNT(stamp) WHERE COMPLETED (사용자 전체)
 *   totalSites       = COUNT(course_site) of ACTIVE courses
 * [사용 위치] PassportService 가 PassportSlotRow 목록을 그룹핑해 조립
 */
public record PassportResponse(Summary summary, List<RegionBlock> regions) {

    public record Summary(int completedCourses, int totalCourses, int completedSites, int totalSites) {}

    public record RegionBlock(Long regionId, String name, List<CourseBlock> courses) {}

    public record CourseBlock(Long courseId, String name, Long pilgrimageId, String status,
                              int completedCount, List<SlotBlock> slots) {}

    /** extPhrase 는 값이 없으면 통째로 null 이다 — 필드 자체는 항상 있다(챕터 8 §3-2). */
    public record SlotBlock(Integer position, Long courseSiteId, String siteName, boolean completed,
                            Long stampId, LocalDateTime completedAt, String userSentence, String photoKey,
                            ExtPhraseResponse extPhrase) {}
}
