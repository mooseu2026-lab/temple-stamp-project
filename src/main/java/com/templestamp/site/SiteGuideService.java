package com.templestamp.site;

import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.site.dto.SiteGuideResponse;
import com.templestamp.site.dto.SiteGuideResponse.Step;
import com.templestamp.site.dto.SiteGuideRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * GET /api/sites/{siteId}/guide — 「사찰 가는 법」 7자리 조립.
 * <p>
 * 사전 7행을 그대로 7자리로 내리되, 이 절에 없는 요소는 자리를 비우지 않고 대체 서술로 채운다.
 * 대체 서술은 그 요소의 의미(사전에 있으므로 없는 절에서도 쓸 수 있다)와, 이 자리로 오는 길·
 * 다음 자리로 가는 길을 이어 붙인 것이다. 그래서 "금강문이 없는 절" 이 아니라
 * "일주문에서 천왕문까지 이렇게 걷는 절" 로 읽힌다.
 * <p>
 * 로그인 여부와 무관하다 — 참배 순서는 개인화할 것이 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SiteGuideService {

    /** 사전이 7행 고정이므로 응답도 항상 7자리다. 이 값이 흔들리면 응답 계약이 깨진 것이다. */
    static final int STEP_COUNT = 7;

    private final SiteMapper siteMapper;
    private final SiteGuideMapper guideMapper;

    public SiteGuideResponse getGuide(Long siteId) {
        // 공개 화면이라 ACTIVE 만 연다. 목록에서 내린 사찰이 링크로 살아 있으면 "내렸다" 가 거짓이 된다.
        Site site = siteMapper.findActiveById(siteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SITE_4040));

        List<SiteGuideRow> rows = guideMapper.findGuideRows(siteId);
        if (rows.size() != STEP_COUNT) {
            // 사전 행이 지워졌거나 sort_no 가 중복된 상태. 사용자에게는 있는 만큼 내려주고 로그를 남긴다.
            // site_element 쪽 FK 가 ON DELETE RESTRICT 라 정상적인 경로로는 일어나지 않는다.
            log.warn("shrine_element 가 {}행이다. 7행이어야 한다. siteId={}", rows.size(), siteId);
        }

        List<Step> steps = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            steps.add(toStep(rows, i));
        }

        return new SiteGuideResponse(site.getSiteId(), site.getName(), List.copyOf(steps));
    }

    /**
     * 있는 자리는 사전 내용에 그 절의 이름·설명을 얹는다. <b>길 안내(passage)는 있든 없든 한 문장씩</b> —
     * 자기 자리로 오는 길이다.
     * <p>
     * 예전에는 없는 자리에 "오는 길 + 다음 자리로 가는 길" 두 문장을 붙였다. 그런데 어떤 자리의
     * '가는 길'은 <b>바로 다음 자리의 '오는 길'과 같은 문장</b>이라, 이웃한 두 칸에 똑같은 문장이 나왔다.
     * 도심 절처럼 산문이 통째로 빈 곳에서는 다섯 문장이 두 번씩 읽혔다 —
     * 하필 이 기능이 가장 필요한 절에서 가장 심했다.
     * <p>
     * 이어 붙이기를 걷어내면 7칸이 P1~P7 을 <b>한 번씩</b> 낸다. 위에서 아래로 읽으면
     * 주차장에서 산신각까지 걷는 순서가 끊기지 않고, 잃는 안내도 없다 —
     * "다음으로 가는 길" 은 다음 칸이 자기 몫으로 이미 말하기 때문이다.
     */
    private Step toStep(List<SiteGuideRow> rows, int i) {
        SiteGuideRow row = rows.get(i);
        boolean present = row.isPresent();
        String passage = row.getPassageMeaning();

        return new Step(
                row.getSortNo(),
                row.getCode(),
                row.getName(),
                present ? row.getLocalName() : null,
                present,
                row.getMeaning(),
                passage,
                present ? row.getEtiquette() : null,
                present ? row.getNote() : null);
    }
}
