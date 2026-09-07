package com.templestamp.verse;

import com.templestamp.global.response.ApiResponse;
import com.templestamp.global.response.ItemsResponse;
import com.templestamp.verse.dto.VerseResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 오관게 다섯 구. 인증 없이 누구나 조회할 수 있다.
 * <p>
 * 개인화된 확장문구는 여기가 아니라 사찰 싱글페이지(/api/sites/{siteId}/page)에서 나간다 —
 * 문구는 코스·계층·노출 이력에 따라 달라져서 구절 하나만으로는 고를 수 없기 때문이다.
 * <p>
 * verseNo 는 gwan_verse.verse_no 이고 DB 에 CHECK(1~5) 가 걸려 있다. 그 제약을 컨트롤러에서
 * 한 번 더 비춰 둔다.
 */
@RestController
@RequestMapping("/api/verses")
@RequiredArgsConstructor
@Validated
@Slf4j
public class VerseController {

    private final PhraseService phraseService;

    /** 5건, verse_no 순. */
    @GetMapping
    public ApiResponse<ItemsResponse<VerseResponse>> list() {
        log.debug("GET /api/verses");
        return ApiResponse.ok(ItemsResponse.of(phraseService.getVerses()));
    }

    /**
     * 0 이하·6 이상은 400 COMMON-4000 이다 — DB 까지 가지 않는다.
     * "없다" 가 아니라 정의역 밖이라 404 가 아니라 400 이 맞다.
     * 1~5 인데 행이 없으면 그때는 404 VERSE-4040 이다.
     */
    @GetMapping("/{verseNo}")
    public ApiResponse<VerseResponse> one(@PathVariable @Min(1) @Max(5) Integer verseNo) {
        log.debug("GET /api/verses/{}", verseNo);
        return ApiResponse.ok(phraseService.getVerse(verseNo));
    }
}
