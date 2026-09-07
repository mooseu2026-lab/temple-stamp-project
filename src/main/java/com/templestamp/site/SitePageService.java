package com.templestamp.site;

import com.templestamp.course.CourseSiteMapper;
import com.templestamp.course.dto.CourseSiteRow;
import com.templestamp.global.config.StampProperties;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.global.type.Tier;
import com.templestamp.global.web.LocaleUtil;
import com.templestamp.pilgrimage.Pilgrimage;
import com.templestamp.pilgrimage.PilgrimageMapper;
import com.templestamp.site.dto.SitePageResponse;
import com.templestamp.stamp.Stamp;
import com.templestamp.stamp.StampMapper;
import com.templestamp.user.UserMapper;
import com.templestamp.verse.ExpansionPhrase;
import com.templestamp.verse.GwanVerse;
import com.templestamp.verse.GwanVerseMapper;
import com.templestamp.verse.Mission;
import com.templestamp.verse.PhraseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 사찰 싱글페이지 조립 전담.
 * <p>
 * siteId 하나만 받는다. course_site.site_id 에 UNIQUE 가 걸려 있어 사찰 ID 로 코스·자리·구절이
 * 전부 결정되기 때문이다. 8개 도메인 조회가 여기서 한 응답으로 합쳐진다.
 * <p>
 * 확장문구는 여기서 고르기만 하고 "본 것" 으로 기록하지 않는다 — 여기는 도착 전 미리보기라,
 * 새로고침만으로 문구 풀이 소진되면 안 된다. 기록은 도장이 완료될 때 한다(챕터 5).
 * <p>
 * 문구가 없으면 500(SITE-5001)인데 미션이 없으면 그냥 null 인 것은 비대칭이 아니라 같은 원칙이다.
 * 미션 부재는 QR 통과 시 VERSE-4041 로 반드시 드러난다.
 * 문구 부재는 어디서도 드러나지 않아 500 이 유일한 경보다.
 * 둘 다 "누락은 반드시 드러난다" 는 같은 원칙의 다른 표현이다.
 */
@Service
@RequiredArgsConstructor
public class SitePageService {

    private final SiteMapper siteMapper;
    private final SiteI18nMapper siteI18nMapper;
    private final SiteViewpointMapper viewpointMapper;
    private final SiteBadgeMapper badgeMapper;
    private final CourseSiteMapper courseSiteMapper;
    private final GwanVerseMapper verseMapper;
    private final PhraseService phraseService;
    private final PilgrimageMapper pilgrimageMapper;
    private final StampMapper stampMapper;
    private final UserMapper userMapper;
    private final StampProperties stampProperties;

    /**
     * @param target     쿼리로 받은 계층. null 이면 users.tier → AGE30 순으로 정한다.
     * @param localeHint 쿼리 locale 또는 Accept-Language 에서 정해진 값. 둘 다 없으면 null 이 오고,
     *                   여기서 users.locale → ko 로 이어 판단한다. 컨트롤러는 사용자를 조회하지 않으므로
     *                   이 두 자리는 Service 의 몫이다.
     * @param userId     null 이면 비로그인 — verifyState 는 null 이고 노출 이력도 남기지 않는다.
     */
    @Transactional
    public SitePageResponse getPage(Long siteId, Tier target, String localeHint, Long userId) {
        // 공개 화면이라 ACTIVE 만 연다. 목록에서 내린 사찰이 링크로 살아 있으면 "내렸다" 가 거짓이 된다.
        Site site = siteMapper.findActiveById(siteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SITE_4040));

        CourseSiteRow slot = courseSiteMapper.findBySiteId(siteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_4000,
                        "어느 코스에도 속하지 않은 사찰입니다."));

        Tier tier = resolveTier(target, userId);
        String locale = resolveLocale(localeHint, userId);

        return new SitePageResponse(
                siteBlock(site, slot, locale),
                verseBlock(slot.getVerseNo()),
                phraseBlock(userId, slot, tier),
                missionBlock(userId, slot, tier),
                viewpoints(siteId),
                badges(siteId),
                new SitePageResponse.RiderInfoBlock(
                        site.getParkingInfo(), site.getAccessInfo(), site.getMealAvailable()),
                verifyState(userId, slot));
    }

    /* ---------------- 계층·언어 결정 ---------------- */

    /** 쿼리 target → users.tier → AGE30. 앞에서 정해지면 뒤는 조회하지 않는다. */
    private Tier resolveTier(Tier target, Long userId) {
        if (target != null) {
            return target;
        }
        return userId == null ? Tier.AGE30 : Tier.orDefault(userMapper.findTier(userId));
    }

    /** localeHint(쿼리·헤더에서 이미 결정됨) → users.locale → ko. */
    private String resolveLocale(String localeHint, Long userId) {
        if (localeHint != null) {
            return localeHint;
        }
        return LocaleUtil.orDefault(userId == null ? null : userMapper.findLocale(userId));
    }

    /* ---------------- 블록별 조립 ---------------- */

    private SitePageResponse.SiteBlock siteBlock(Site site, CourseSiteRow slot, String locale) {
        String name = site.getName();
        String description = null;

        // 요청 언어 번역이 없으면 ko 로 떨어뜨린다. 빈 화면보다 원문이 낫다.
        var i18n = siteI18nMapper.findBySiteIdAndLocale(site.getSiteId(), locale)
                .or(() -> siteI18nMapper.findBySiteIdAndLocale(site.getSiteId(), "ko"))
                .orElse(null);
        if (i18n != null) {
            name = i18n.getName();
            description = i18n.getDescription();
        }

        return new SitePageResponse.SiteBlock(
                site.getSiteId(), name, description,
                slot.getCourseId(), slot.getCourseSiteId(), slot.getPosition(),
                site.getLatitude(), site.getLongitude(),
                site.getVerifyRadius(), site.getQrLocationHint());
    }

    private SitePageResponse.VerseBlock verseBlock(Integer verseNo) {
        GwanVerse verse = verseMapper.findByVerseNo(verseNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERSE_4040));
        return new SitePageResponse.VerseBlock(
                verse.getVerseNo(), verse.getHanja(), verse.getTextKo(), verse.getTheme());
    }

    /**
     * 요청 계층에 문구가 없으면 기본 계층(AGE30)으로 한 번 물러난다.
     * 계층별 원고가 아직 다 차지 않은 동안 특정 계층 사용자만 화면이 비는 것을 막기 위해서다.
     * 그래도 없으면 원고가 통째로 비어 있다는 뜻이라 SITE-5001 로 끊는다 — 조용히 빈 블록을 내보내면
     * 콘텐츠가 빠진 것을 아무도 모른 채 배포된다.
     */
    private SitePageResponse.PhraseBlock phraseBlock(Long userId, CourseSiteRow slot, Tier tier) {
        ExpansionPhrase phrase = phraseService.pickPhrase(
                userId, slot.getCourseId(), slot.getVerseNo(), tier);

        if (phrase == null && tier != Tier.AGE30) {
            phrase = phraseService.pickPhrase(
                    userId, slot.getCourseId(), slot.getVerseNo(), Tier.AGE30);
        }
        if (phrase == null) {
            throw new BusinessException(ErrorCode.SITE_5001,
                    "확장문구가 없습니다. (구절 %d, 대상 %s)".formatted(slot.getVerseNo(), tier));
        }
        return new SitePageResponse.PhraseBlock(
                phrase.getExpansionPhraseId(), phrase.getVersionNo(), phrase.getTextKo());
    }

    /**
     * 도착 전에 미리 보여 주는 미션. 여기서는 "받은 것" 으로 기록하지 않는다 —
     * 실제 배정은 QR 통과 시점에 일어난다.
     */
    private SitePageResponse.MissionBlock missionBlock(Long userId, CourseSiteRow slot, Tier tier) {
        // 배정과 <b>같은 규칙</b>으로 고른다(챕터 10). 미리 본 것과 받는 것이 다른 규칙으로 갈리면
        // "아까 본 과제가 아니다" 를 설명할 길이 없다. 다만 여기서는 노출 이력을 남기지 않는다.
        Mission mission = phraseService.peekMission(
                userId, slot.getCourseId(), slot.getSiteId(), slot.getVerseNo(), tier);
        if (mission == null) {
            return null;
        }
        return new SitePageResponse.MissionBlock(
                mission.getMissionId(), mission.getVariantNo(), mission.getBody());
    }

    private List<SitePageResponse.ViewpointBlock> viewpoints(Long siteId) {
        return viewpointMapper.findBySiteId(siteId).stream()
                .map(v -> new SitePageResponse.ViewpointBlock(
                        v.getSortNo(), v.getLocationDesc(), v.getBestTime(), v.getWhatToSee()))
                .toList();
    }

    private List<SitePageResponse.BadgeBlock> badges(Long siteId) {
        return badgeMapper.findBySiteId(siteId).stream()
                .map(b -> new SitePageResponse.BadgeBlock(b.getBadgeType(), b.getDescription()))
                .toList();
    }

    /**
     * 지금 이 자리의 인증이 어디까지 왔는지. 비로그인이거나 아직 순례를 시작하지 않았으면 null.
     * expiresAt 은 DB 컬럼이 아니라 gps_verified_at + 60분 계산값이다.
     */
    private SitePageResponse.VerifyStateBlock verifyState(Long userId, CourseSiteRow slot) {
        if (userId == null) {
            return null;
        }
        // 교재 [기본 40] 의 매퍼는 Optional 이 아니라 null 을 준다(uk 기준 1건 또는 없음).
        Pilgrimage pilgrimage = pilgrimageMapper.findByUserAndCourse(userId, slot.getCourseId());
        if (pilgrimage == null) {
            return null;
        }

        Stamp stamp = stampMapper
                .findLatest(pilgrimage.getPilgrimageId(), slot.getCourseSiteId())
                .orElse(null);
        if (stamp == null) {
            return new SitePageResponse.VerifyStateBlock(
                    pilgrimage.getPilgrimageId(), null, null, null);
        }

        LocalDateTime expiresAt = stamp.getGpsVerifiedAt() == null || !stamp.getVerifyStatus().isInProgress()
                ? null
                : stamp.getGpsVerifiedAt().plusMinutes(stampProperties.sessionMinutes());

        return new SitePageResponse.VerifyStateBlock(
                pilgrimage.getPilgrimageId(),
                stamp.getStampId(),
                stamp.getVerifyStatus().name(),
                expiresAt);
    }
}
