package com.templestamp.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.templestamp.global.web.LocaleUtil;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 정적 콘텐츠(사찰 가는 법, 다국어 문자열)를 클래스패스에서 한 번 읽어 메모리에 둔다.
 * <p>
 * DB 에 넣지 않는 이유는 이 값들이 배포 단위로 바뀌는 문서이기 때문이다. 운영 중 수정할 일이
 * 생기면 배포로 반영하는 편이, 스키마와 관리 화면을 따로 만드는 것보다 오래 간다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StaticContentLoader {

    private static final String GUIDE_PATH = "static-content/guide.%s.json";
    private static final String I18N_PATH = "static-content/i18n/%s.json";
    private static final List<String> SUPPORTED_LANGS = List.of("ko", "en");

    private final ObjectMapper objectMapper;
    private final Map<String, JsonNode> cache = new ConcurrentHashMap<>();

    @PostConstruct
    void warmUp() {
        // 기동 때 한 번 읽어 둔다. 파일이 깨져 있으면 첫 요청이 아니라 기동에서 드러나는 게 낫다.
        load(String.format(GUIDE_PATH, LocaleUtil.DEFAULT_LOCALE));
        SUPPORTED_LANGS.forEach(lang -> load(String.format(I18N_PATH, lang)));
        log.info("정적 콘텐츠 {}건 로드 완료", cache.size());
    }

    /** 사찰 가는 법. 요청 언어 파일이 없으면 한국어로 떨어뜨린다. */
    public JsonNode getGuide(String acceptLanguage) {
        String lang = LocaleUtil.resolve(acceptLanguage);
        JsonNode node = cache.get(String.format(GUIDE_PATH, lang));
        return node != null ? node : require(String.format(GUIDE_PATH, LocaleUtil.DEFAULT_LOCALE));
    }

    public JsonNode getI18n(String lang) {
        String normalized = SUPPORTED_LANGS.contains(lang) ? lang : LocaleUtil.DEFAULT_LOCALE;
        return require(String.format(I18N_PATH, normalized));
    }

    private JsonNode require(String path) {
        JsonNode node = cache.get(path);
        if (node == null) {
            node = load(path);
        }
        if (node == null) {
            throw new BusinessException(ErrorCode.COMMON_4040);
        }
        return node;
    }

    private JsonNode load(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            log.warn("정적 콘텐츠 없음: {}", path);
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            JsonNode node = objectMapper.readTree(in);
            cache.put(path, node);
            return node;
        } catch (IOException e) {
            throw new IllegalStateException("정적 콘텐츠 로드 실패: " + path, e);
        }
    }
}
