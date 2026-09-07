package com.templestamp.manuscript;

import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 어느 원고를 보여 줄지 고른다(챕터 8 §3-1).
 * <pre>
 *   후보 = 그 사찰의 APPROVED 원고 (변형 번호 순)
 *   없으면 → 기본 원고(site_id NULL)
 *   선택   = 후보[ userId mod 후보 수 ]
 * </pre>
 * <b>난수가 아니라 나머지 연산인 이유</b>: 같은 사람이 같은 사찰에서 늘 같은 글을 보아야 한다.
 * 무작위로 고르면 화면을 새로 고칠 때마다 글이 바뀌고, 사용자는 읽던 문장을 잃는다.
 * 사람마다 다른 글이 배정되는 성질은 그대로 남는다(사용자 번호가 다르므로).
 * <p>
 * 고른 결과는 도장 행에 적어 둔다 — 이 저장소에서는 도장 행이 곧 인증 세션이라,
 * "세션에 저장" 과 "발행 시 복사" 가 같은 칸 하나로 끝난다(챕터 8 §3-1·함정 3).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManuscriptSelector {

    /** 기본 원고의 site_key. 생성 컬럼이 NULL 을 0 으로 접는다. */
    private static final long DEFAULT_SITE_KEY = 0L;

    private final ManuscriptMapper manuscriptMapper;

    /**
     * 미션 원고. 기본 원고까지 없으면 500 이 아니라 409 다 —
     * 사용자가 잘못한 것이 아니라 콘텐츠가 아직 없는 것이고, 그 사실을 그대로 알려야 한다.
     */
    public Manuscript pickMission(Long userId, Long siteId, Integer verseNo) {
        Manuscript picked = pick(userId, siteId, verseNo, Manuscript.MISSION);
        if (picked == null) {
            throw new BusinessException(ErrorCode.MS_4093);
        }
        return picked;
    }

    /** 확장문구. 없으면 null — 문구가 없다고 도장을 막을 이유는 없다. */
    public Manuscript pickExt(Long userId, Long siteId, Integer verseNo) {
        return pick(userId, siteId, verseNo, Manuscript.EXT);
    }

    public Manuscript findById(Long manuscriptId) {
        return manuscriptId == null ? null : manuscriptMapper.findById(manuscriptId).orElse(null);
    }

    private Manuscript pick(Long userId, Long siteId, Integer verseNo, String kind) {
        List<Manuscript> candidates = siteId == null
                ? List.of()
                : manuscriptMapper.findApproved(siteId, verseNo, kind);
        if (candidates.isEmpty()) {
            candidates = manuscriptMapper.findApproved(DEFAULT_SITE_KEY, verseNo, kind);
        }
        if (candidates.isEmpty()) {
            return null;
        }
        int index = (int) Math.floorMod(userId, candidates.size());
        return candidates.get(index);
    }
}
