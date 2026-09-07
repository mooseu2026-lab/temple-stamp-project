package com.templestamp.course.dto;

/**
 * 코스별 진행률. pilgrimage 없으면 null 로 내려간다.
 * total 은 항상 5로 고정(코스당 사찰 5곳).
 * completedCount 는 COUNT(stamp) WHERE verify_status='COMPLETED' GROUP BY pilgrimage.
 * [사용 위치] CourseService·PilgrimageService·StampService 세 곳에서 재사용
 */
public record ProgressResponse(Long pilgrimageId, int completedCount, int total, String status) {

    /** 코스당 사찰 수. 여권 칸 수·완주 판정도 이 값을 쓴다 — 여기 하나만 바꾸면 전부 따라온다 */
    public static final int SITES_PER_COURSE = 5;

    public static ProgressResponse of(Long pilgrimageId, int completedCount, String status) {
        return new ProgressResponse(pilgrimageId, completedCount, SITES_PER_COURSE, status);
    }
}
