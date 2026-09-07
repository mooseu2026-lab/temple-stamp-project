package com.templestamp.verse;

import com.templestamp.global.type.Tier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 미션 — 다짐 한 문장 과제. 구절·대상별로 여러 변형을 두고 하나를 골라 출제한다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Mission {

    public static final String DRAFT = "DRAFT";
    public static final String REVIEWED = "REVIEWED";

    /** 다짐 문장 최소 길이. 한 문장은 써야 기록으로 남을 값이 된다. */
    public static final int MIN_SENTENCE_LENGTH = 10;

    private Long missionId;
    /** SITE·COMMON 과제는 null 이다. */
    private Integer verseNo;
    private Tier tier;
    /** 출처 축. 없으면 VERSE 로 읽는다(옛 행과 같은 뜻). */
    private MissionScope scope;
    /** scope=SITE 일 때만 채워진다. */
    private Long siteId;
    private Integer variantNo;
    private String body;
    private String originRef;
    private String reviewStatus;
}
