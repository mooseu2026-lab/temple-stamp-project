package com.templestamp.course.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 코스 상세 화면의 사찰 한 칸을 내려주는 DTO Record입니다.
 * 좌표와 인증 반경을 앱에 전달해 이후 거리 계산을 단말이 하도록 만드는 유일한 통로입니다.
 * (서버는 요청으로 좌표를 받지 않는다 — CoordinateFieldGuard. 내보내기만 한다)
 * [사용 위치] CourseService 가 CourseSiteRow → 변환, CourseDetailResponse 에 담김
 */
public record CourseSiteResponse(
        Long courseSiteId,
        Long siteId,
        Integer position,
        String siteName,
        BigDecimal latitude,
        BigDecimal longitude,
        Integer verifyRadius,
        String qrLocationHint,

        /**
         * v4 — 이 자리에서 인증할 수 있는 사찰들. 대표(siteId)도 여기 들어간다(track MAIN, sortNo 1).
         * 과포화(is_congested=1) 후보는 빠지므로 <b>비어 있을 수 있다</b> — 그때는 대표 한 곳만 쓰면 된다.
         */
        List<CandidateResponse> candidates
) {
    public static CourseSiteResponse from(CourseSiteRow row, List<CandidateResponse> candidates) {
        return new CourseSiteResponse(row.getCourseSiteId(), row.getSiteId(), row.getPosition(),
                row.getSiteName(), row.getLatitude(), row.getLongitude(),
                row.getVerifyRadius(), row.getQrLocationHint(),
                candidates == null ? List.of() : candidates);
    }
}
