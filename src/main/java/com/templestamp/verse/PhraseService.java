package com.templestamp.verse;

import com.templestamp.admin.dto.MissionSaveRequest;
import com.templestamp.admin.dto.PhraseSaveRequest;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.global.type.Tier;
import com.templestamp.verse.dto.VerseResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 오관게·확장문구·미션을 사람에 맞춰 골라 주는 곳.
 * <p>
 * 규칙은 하나다: 이 코스에서 아직 안 본 것을 먼저 준다. 다섯 버전을 다 봤으면 가장 오래전에
 * 본 것을 다시 준다. 완전히 막지 않는 이유는, 코스를 다시 도는 사람에게 화면이 비어 버리면
 * 그게 더 나쁜 경험이기 때문이다.
 * <p>
 * 노출 이력은 (사용자, 코스) 단위다. 코스가 바뀌면 문구 풀이 다시 열린다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PhraseService {

    private final GwanVerseMapper verseMapper;
    private final ExpansionPhraseMapper phraseMapper;
    private final MissionMapper missionMapper;
    private final PhraseSeenMapper phraseSeenMapper;
    private final TaskSeenMapper taskSeenMapper;

    /* ---------------- 오관게 ---------------- */

    public List<VerseResponse> getVerses() {
        return verseMapper.findAll().stream()
                .map(v -> new VerseResponse(v.getVerseNo(), v.getHanja(), v.getTextKo(), v.getTheme()))
                .toList();
    }

    public VerseResponse getVerse(Integer verseNo) {
        GwanVerse verse = verseMapper.findByVerseNo(verseNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERSE_4040));
        return new VerseResponse(verse.getVerseNo(), verse.getHanja(), verse.getTextKo(), verse.getTheme());
    }

    /* ---------------- 확장문구 ---------------- */

    /**
     * 확장문구 하나를 고른다. <b>노출 이력을 남기지 않는다.</b>
     * 등록된 문구가 없으면 null — 호출부가 판단한다.
     * <p>
     * 예전에는 여기서 바로 markSeen 을 했는데, 이 메서드를 부르는 곳이 사찰 싱글페이지
     * (도착 전 미리보기)라 화면을 새로고침할 때마다 문구 풀이 소진됐다. 도장을 찍기도 전에
     * "본 것" 이 되어 버리는 셈이다. 그래서 고르는 일과 기록하는 일을 갈라 두었다.
     * <p>
     * 기록은 도장이 실제로 COMPLETED 로 넘어갈 때 {@link #markPhraseSeen} 으로 한다(챕터 5).
     * 그때까지 phrase_seen 은 계속 0행이고, 같은 코스 안 반복 방지도 그때까지는 동작하지 않는다.
     * 문구가 반복돼 보이는 것은 버그가 아니라 아직 붙이지 않은 기능이다.
     */
    @Transactional(readOnly = true)
    public ExpansionPhrase pickPhrase(Long userId, Long courseId, Integer verseNo, Tier tier) {
        Tier target = Tier.orDefault(tier);

        if (userId == null || courseId == null) {
            return phraseMapper.findUnseen(null, null, verseNo, target).orElse(null);
        }

        // 완화 세 단계. 위에서부터 내려가며 처음 걸리는 것을 준다(챕터 10).
        //   ① 안 읽었고 + 이 코스에서 쓴 버전과도 겹치지 않는 것
        //   ② 안 읽은 것(버전 중복 허용)
        //   ③ 가장 오래전에 읽은 것
        // 어느 단계에서 나왔는지는 응답에 싣지 않는다 — 사용자가 알 일이 아니고, 실으면 계약이 하나 는다.
        return phraseMapper.findUnseenWithNewVersion(userId, courseId, verseNo, target)
                .or(() -> phraseMapper.findUnseen(userId, courseId, verseNo, target))
                .or(() -> phraseMapper.findLeastRecentlySeen(userId, courseId, verseNo, target))
                .orElse(null);
    }

    /**
     * 그 문구를 실제로 읽은 것으로 기록한다. 챕터 5 의 도장 완료 처리에서 부른다.
     * INSERT IGNORE 라 같은 조합이 두 번 들어와도 조용히 넘어간다.
     * <p>
     * 비로그인(userId null)은 남길 곳이 없으므로 아무 일도 하지 않는다.
     */
    @Transactional
    public void markPhraseSeen(Long userId, Long courseId, Long expansionPhraseId) {
        if (userId == null || courseId == null || expansionPhraseId == null) {
            return;
        }
        phraseSeenMapper.markSeen(userId, courseId, expansionPhraseId);
    }

    /* ---------------- 미션 ---------------- */

    /**
     * 그 자리에서 낼 미션. 안 받아 본 것 우선. 뽑은 미션은 받은 것으로 기록한다.
     * 미션 없이 도장을 끝낼 수 없으므로 여기서 못 찾으면 예외로 끊는다.
     */
    @Transactional
    /**
     * 클라이언트가 보낸 확장문구가 <b>이 구절·이 계층의 것인지</b> 서버가 확인한다.
     * 아니면 {@code null} 을 돌려주고 기록만 건너뛴다 — 다짐 제출 자체를 실패시킬 이유가 아니다.
     * 확인 없이 그대로 기록하면 사용자가 아무 번호나 보내 남의 계층 문구를 "읽었다" 로 만들 수 있다.
     */
    public Long validatePhraseFor(Long expansionPhraseId, Integer verseNo, Tier tier) {
        if (expansionPhraseId == null) {
            return null;
        }
        return phraseMapper.existsForVerseAndTier(expansionPhraseId, verseNo, tier.name()) ? expansionPhraseId : null;
    }

    /**
     * 미션을 배정하고 <b>노출 이력(task_seen)까지 남긴다.</b> 클래스가 {@code readOnly = true} 라
     * 여기서 되돌리지 않으면, 읽기 전용 트랜잭션에서 부르는 호출자가 하나만 생겨도
     * {@code Connection is read-only} 로 500 이 된다.
     */
    @Transactional
    public Mission pickMission(Long userId, Long courseId, Long siteId, Integer verseNo, Tier tier) {
        Mission mission = chooseMission(userId, courseId, siteId, verseNo, tier);
        taskSeenMapper.markSeen(userId, courseId, mission.getMissionId());
        return mission;
    }

    /**
     * 싱글페이지의 미리보기. <b>노출 이력을 남기지 않는다</b> — 실제 배정은 QR 통과 시점이다.
     * 고르는 규칙은 배정과 같아야 한다. 미리 본 것과 받는 것이 다른 규칙으로 갈리면
     * "아까 본 과제가 아니다" 를 설명할 길이 없다.
     */
    public Mission peekMission(Long userId, Long courseId, Long siteId, Integer verseNo, Tier tier) {
        return chooseMission(userId, courseId, siteId, verseNo, tier);
    }

    /**
     * 세 풀에서 <b>50 / 30 / 20</b> 가중으로 하나를 고른다(기획 명세 §5.4 · 챕터 10).
     * <p>
     * ① 아직 안 본 과제가 남아 있는 풀을 한 번에 세고
     * ② <b>비어 있지 않은 풀들 사이에서만</b> 가중을 나눠 풀 하나를 고르고
     * ③ 그 풀에서 미노출 우선으로 하나를 뽑는다.
     * <p>
     * 가중을 남은 풀들 사이에서만 나누는 것이 요점이다. 전체 100 을 기준으로 뽑으면
     * SITE 과제가 아직 없는 사찰에서 50%의 확률로 아무것도 못 고른다.
     * <p>
     * 셋 다 비면 대체안을 준다 — 미션 없이 도장을 끝낼 수 없다는 챕터 5 결정 그대로다.
     * 그때 WARN 을 한 줄 남긴다. 조용히 넘어가면 콘텐츠가 바닥난 것을 아무도 모른다.
     */
    private Mission chooseMission(Long userId, Long courseId, Long siteId, Integer verseNo, Tier tier) {
        Tier target = Tier.orDefault(tier);

        List<MissionScope> available = missionMapper
                .countUnseenByScope(userId, courseId, siteId, verseNo, target).stream()
                .filter(c -> c.cnt() > 0)
                .map(MissionMapper.ScopeCount::scope)
                .toList();

        if (!available.isEmpty()) {
            MissionScope picked = weightedPick(available);
            Optional<Mission> found = missionMapper
                    .findUnseenInScope(userId, courseId, siteId, verseNo, target, picked);
            if (found.isPresent()) {
                return found.get();
            }
            // 세고 나서 고르는 사이에 다른 요청이 그 하나를 가져간 경우. 드물지만 있다.
            log.debug("고른 풀이 비어 있었다. scope={} site={} verse={}", picked, siteId, verseNo);
        }

        log.warn("TASK-POOL-EMPTY site={} verse={} tier={} — 안 본 과제가 없어 대체안을 낸다",
                siteId, verseNo, target);
        return missionMapper.findAnyForFallback(siteId, verseNo, target)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERSE_4041,
                        "이 사찰·구절·대상에 등록된 과제가 없습니다. (사찰 %s, 구절 %s, 대상 %s)"
                                .formatted(siteId, verseNo, target)));
    }

    /**
     * 남아 있는 풀들 사이에서 가중치로 하나를 고른다.
     * <p>
     * 예를 들어 SITE 가 비어 있으면 VERSE 30 · COMMON 20 이 남고, 실제 확률은 60% · 40% 가 된다.
     * 가중은 {@link MissionScope} 가 들고 있다 — 숫자를 두 곳에 적지 않는다.
     */
    private MissionScope weightedPick(List<MissionScope> pools) {
        int total = pools.stream().mapToInt(MissionScope::weight).sum();
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (MissionScope pool : pools) {
            roll -= pool.weight();
            if (roll < 0) {
                return pool;
            }
        }
        return pools.get(pools.size() - 1);   // 부동소수가 아니라 정수라 여기 오지 않는다
    }

    /** 도장 검증 단계에서 쓰는 조회. 여기서는 노출 이력을 남기지 않는다. */
    public Mission getMission(Long missionId) {
        return missionMapper.findById(missionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERSE_4041));
    }

    /* ---------------- 관리자 등록 ---------------- */

    /**
     * (구절·대상·버전) 이 같으면 덮어쓴다. 원고 파일을 다시 밀어 넣어도 중복되지 않는다.
     * versionNo 를 비우면 그 조합의 다음 빈 번호를 채번한다 — 편집자가 번호를 세지 않아도 되게.
     */
    @Transactional
    public Long savePhrase(PhraseSaveRequest request) {
        int versionNo = request.versionNo() != null
                ? request.versionNo()
                : phraseMapper.nextVersionNo(request.verseNo(), request.tier());

        if (versionNo > 5) {
            throw new BusinessException(ErrorCode.COMMON_4090,
                    "이 구절·대상은 이미 5개 버전이 모두 등록돼 있습니다. 덮어쓸 버전을 지정해 주세요.");
        }

        ExpansionPhrase phrase = ExpansionPhrase.builder()
                .verseNo(request.verseNo())
                .tier(request.tier())
                .versionNo(versionNo)
                .textKo(request.textKo())
                .reviewStatus(request.reviewStatus() != null
                        ? request.reviewStatus() : ExpansionPhrase.DRAFT)
                .build();
        phraseMapper.upsert(phrase);
        return phrase.getExpansionPhraseId();
    }

    @Transactional
    public Long saveMission(MissionSaveRequest request) {
        MissionScope scope = request.scope() != null ? request.scope() : MissionScope.VERSE;
        validateScope(scope, request.verseNo(), request.siteId());

        int variantNo = request.variantNo() != null
                ? request.variantNo()
                : missionMapper.nextVariantNo(request.verseNo(), request.tier(), scope, request.siteId());

        Mission mission = Mission.builder()
                .verseNo(request.verseNo())
                .tier(request.tier())
                .scope(scope)
                .siteId(request.siteId())
                .variantNo(variantNo)
                .body(request.body())
                .originRef(request.originRef())
                .reviewStatus(request.reviewStatus() != null
                        ? request.reviewStatus() : Mission.DRAFT)
                .build();
        missionMapper.upsert(mission);
        return mission.getMissionId();
    }

    /**
     * 축과 값이 맞는지 넣기 전에 본다. DB 의 {@code chk_mission_scope_ref} 가 최종 방어선이지만,
     * 제약 위반은 사람이 읽을 수 없는 메시지로 돌아온다 — 여기서 무엇이 틀렸는지 말해 준다.
     */
    private void validateScope(MissionScope scope, Integer verseNo, Long siteId) {
        boolean ok = switch (scope) {
            case SITE -> siteId != null && verseNo == null;
            case VERSE -> siteId == null && verseNo != null;
            case COMMON -> siteId == null && verseNo == null;
        };
        if (!ok) {
            throw new BusinessException(ErrorCode.COMMON_4000,
                    "과제 축이 맞지 않습니다. SITE 는 사찰만, VERSE 는 구절만, COMMON 은 둘 다 비웁니다.");
        }
    }

    /* ---------------- 통계 ---------------- */

    public int seenPhraseCount(Long userId) {
        return phraseSeenMapper.countByUserId(userId);
    }

    public int totalPhraseCount() {
        return phraseMapper.countAll();
    }
}
