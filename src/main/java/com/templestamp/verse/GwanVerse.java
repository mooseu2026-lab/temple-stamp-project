package com.templestamp.verse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 오관게(五觀偈) 다섯 구. 구절 번호가 그대로 PK 다 — 1~5 외에는 존재하지 않기 때문이다.
 * 코스의 1번 자리는 1구, 5번 자리는 5구를 받는다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GwanVerse {

    private Integer verseNo;
    private String hanja;
    private String textKo;
    private String theme;
}
