package com.templestamp.site.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 사찰 싱글페이지에 필요한 8개 블록을 한 번의 요청으로 조립해 내려주는 DTO Record입니다.
 * 8개 도메인의 조회 결과가 이 응답 하나로 합쳐집니다.
 * 블록들을 중첩 record 로 두는 이유 — 함께 쓰이고 함께 바뀌기 때문.
 * [사용 위치] SitePageService(조립 전담) → SiteController 반환
 */
public record SitePageResponse(
        SiteBlock site,
        VerseBlock verse,
        PhraseBlock expansionPhrase,
        MissionBlock mission,
        List<ViewpointBlock> viewpoints,
        List<BadgeBlock> badges,
        RiderInfoBlock riderInfo,
        VerifyStateBlock verifyState      // 비로그인이면 null
) {

    /** 사찰 기본 정보와 위치·인증 기준. description 은 site_i18n(locale), ko 행 없으면 null */
    public record SiteBlock(Long siteId, String name, String description, Long courseId, Long courseSiteId,
                            Integer position, BigDecimal latitude, BigDecimal longitude,
                            Integer verifyRadius, String qrLocationHint) {}

    /** 그 사찰이 담당하는 오관게 한 구절 — gwan_verse (course_site.verse_no 로 조회). 항상 공개 */
    public record VerseBlock(Integer verseNo, String hanja, String textKo, String theme) {}

    /** tier 와 구절 기준으로 뽑힌 확장문구 1편 — expansion_phrase id, version_no, text_ko */
    public record PhraseBlock(Long expansionPhraseId, Integer versionNo, String text) {}

    /** 그 자리에서 수행할 미션 1편 — 도착 전에 미리 보여주는 것이 설계 의도 */
    public record MissionBlock(Long missionId, Integer variantNo, String body) {}

    /** 사찰의 추천 조망 지점과 좋은 시간대 */
    public record ViewpointBlock(Integer sortNo, String locationDesc, String bestTime, String whatToSee) {}

    /** 그 사찰이 가진 뱃지 종류와 설명 */
    public record BadgeBlock(String badgeType, String description) {}

    /** 라이더를 위한 주차·진입·식사 가능 여부 — site.parking_info, access_info, meal_available */
    public record RiderInfoBlock(String parkingInfo, String accessInfo, String mealAvailable) {}

    /** 현재 인증이 3단계 중 어디까지 왔는지 — expiresAt 은 gps_verified_at + 60분 계산값 */
    public record VerifyStateBlock(Long pilgrimageId, Long stampId, String status, LocalDateTime expiresAt) {}
}
