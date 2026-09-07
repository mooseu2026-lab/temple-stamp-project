package com.templestamp.meditation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** meditation 테이블 도메인 클래스 — 명상 108선 한 편. ACTIVE 만 노출된다. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Meditation {

    public static final String DRAFT = "DRAFT";
    public static final String ACTIVE = "ACTIVE";

    private Long meditationId;
    private Integer categoryNo;
    private String title;
    private String script;
    private String audioKey;
    private Integer durationSec;
    private Integer sortNo;
    private String status;

    public boolean isActive() { return ACTIVE.equals(status); }
}
