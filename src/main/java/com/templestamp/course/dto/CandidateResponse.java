// src/main/java/com/templestamp/course/dto/CandidateResponse.java
package com.templestamp.course.dto;

/**
 * CourseSiteResponse.candidates 의 한 건. 대표 사찰도 candidates 에 포함된다(track MAIN, sortNo 1).
 * <p>
 * {@code congested} 는 Q3 과포화 사찰이다. <b>목록에서 빼지 않는다</b> — 그 자리의 유일한 후보가
 * 과포화인 경우가 있어서(부산·동부 1·2구), 빼면 "대신 갈 곳이 없다" 가 아니라 "갈 곳이 없다" 로 읽힌다.
 * 화면은 "혼잡" 배지를 붙이고 지도에서 강조하지 않는다.
 */
public record CandidateResponse(Long siteId, String siteName, String track, Integer sortNo,
                                String routeNote, boolean isStar, String servingNote,
                                boolean congested) {
    public static CandidateResponse from(SlotCandidateRow r) {
        return new CandidateResponse(r.getSiteId(), r.getSiteName(), r.getTrack(), r.getSortNo(),
                r.getRouteNote(), Boolean.TRUE.equals(r.getIsStar()), r.getServingNote(),
                Boolean.TRUE.equals(r.getCongested()));
    }
}
