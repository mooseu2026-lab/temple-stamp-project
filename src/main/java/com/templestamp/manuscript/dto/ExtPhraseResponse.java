package com.templestamp.manuscript.dto;

import com.templestamp.manuscript.Manuscript;

/**
 * 도장에 실린 확장문구. <b>필드는 항상 있고 값이 없으면 통째로 null</b> 이다
 * (「가는 법」의 present:false 와 같은 원칙 — 키가 사라지면 프론트가 분기를 두 벌 만든다).
 * <p>
 * 도장이 발행될 때 그 시점의 원고를 고정하므로, 나중에 원고가 퇴역·수정돼도 이 값은 바뀌지 않는다.
 * [사용 위치] 도장 발행 응답 · by-slot · 여권
 */
public record ExtPhraseResponse(String title, String body, Integer variantNo) {

    public static ExtPhraseResponse from(Manuscript manuscript) {
        return manuscript == null ? null
                : new ExtPhraseResponse(manuscript.getTitle(), manuscript.getBody(), manuscript.getVariantNo());
    }

    public static ExtPhraseResponse of(String title, String body, Integer variantNo) {
        return title == null ? null : new ExtPhraseResponse(title, body, variantNo);
    }
}
