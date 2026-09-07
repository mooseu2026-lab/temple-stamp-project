package com.templestamp.manuscript.dto;

import com.templestamp.manuscript.Manuscript;

/**
 * 이 세션에서 보여 주는 미션 원고(챕터 8 §3-1).
 * <p>
 * {@code siteId} 를 함께 싣는 이유는 <b>기본 원고를 받았는지</b>를 화면과 점검이 구분해야 하기 때문이다 —
 * 사찰 전용 원고가 아직 없으면 여기가 {@code null} 이다.
 * {@code manuscriptId} 는 세션이 살아 있는 동안 바뀌지 않는다(원고가 그 사이 퇴역해도 그대로다).
 * <p>
 * 값이 없으면 통째로 {@code null} 이고 필드 자체는 항상 있다(확장문구와 같은 원칙).
 * [사용 위치] QR 통과 응답 · 스탬프 조회 · by-slot
 */
public record ManuscriptTextResponse(Long manuscriptId, Long siteId, String title, String body, Integer variantNo) {

    public static ManuscriptTextResponse from(Manuscript m) {
        return m == null ? null
                : new ManuscriptTextResponse(m.getManuscriptId(), m.getSiteId(),
                        m.getTitle(), m.getBody(), m.getVariantNo());
    }
}
