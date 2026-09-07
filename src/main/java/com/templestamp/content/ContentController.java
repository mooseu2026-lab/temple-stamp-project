package com.templestamp.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.templestamp.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 정적 콘텐츠. 인증 없이 열린다. dto 를 두지 않고 JSON 을 그대로 통과시킨다 —
 * 문서 구조가 바뀔 때마다 record 를 따라 고치는 것보다 파일 하나만 바꾸는 게 낫다.
 */
@Validated
@RestController
@RequestMapping("/api/content")
@RequiredArgsConstructor
public class ContentController {

    private final StaticContentLoader loader;

    @GetMapping("/guide")
    public ApiResponse<JsonNode> getGuide(
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ApiResponse.ok(loader.getGuide(acceptLanguage));
    }

    @GetMapping("/i18n/{lang}")
    public ApiResponse<JsonNode> getI18n(@PathVariable String lang) {
        return ApiResponse.ok(loader.getI18n(lang));
    }
}
