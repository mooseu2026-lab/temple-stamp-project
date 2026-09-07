package com.templestamp.verse;

import com.templestamp.global.type.Tier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 확장문구. 7 tier × 5구 × 5버전 = 175편.
 * 같은 구절이라도 보는 사람에 따라 다른 말이 필요해서 tier 별로 갈라 둔다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpansionPhrase {

    public static final String DRAFT = "DRAFT";
    public static final String REVIEWED = "REVIEWED";

    private Long expansionPhraseId;
    private Integer verseNo;
    private Tier tier;
    private Integer versionNo;
    private String textKo;
    private String reviewStatus;

    public boolean isReviewed() {
        return REVIEWED.equals(reviewStatus);
    }
}
