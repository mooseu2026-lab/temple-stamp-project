package com.templestamp.verse.dto;

/**
 * 오관게 다섯 구절 중 한 구의 원문과 해석을 내려주는 DTO Record입니다.
 * 인증 없이 누구나 조회할 수 있습니다.
 * [사용 위치] VerseService → Controller. 필드는 SitePageResponse.VerseBlock 과 동일 유지
 */
public record VerseResponse(
        Integer verseNo,
        String hanja,
        String textKo,
        String theme
) {
}
