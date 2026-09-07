package com.templestamp.verse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 확장문구 노출 이력. 같은 코스를 걷는 동안 같은 문구를 두 번 보여 주지 않기 위한 기록. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhraseSeen {

    private Long phraseSeenId;
    private Long userId;
    private Long courseId;
    private Long expansionPhraseId;
    private LocalDateTime seenAt;
}
