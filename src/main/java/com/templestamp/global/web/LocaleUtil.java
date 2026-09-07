package com.templestamp.global.web;

import java.util.List;
import java.util.Locale;

/**
 * 요청 언어 결정. 지원 언어는 ko/en/ja/zh 네 가지다.
 * <p>
 * 우선순위는 <b>쿼리 locale → Accept-Language → users.locale → ko</b> 인데, 이 클래스는 앞의 둘만 맡는다.
 * 뒤의 둘은 로그인 여부를 아는 Service 의 몫이다 — 컨트롤러는 사용자를 조회하지 않기 때문이다.
 * <p>
 * 그래서 메서드가 둘로 나뉜다.
 * <ul>
 *   <li>{@link #resolve(String)} — 사용자 맥락이 없는 곳. 못 고르면 곧바로 ko 로 떨어진다.</li>
 *   <li>{@link #hint(String, String)} — 뒤에 users.locale 이 기다리는 곳. 못 고르면 <b>null</b> 을 준다.
 *       여기서 ko 를 반환해 버리면 users.locale 이 영영 쓰이지 않는다.</li>
 * </ul>
 * WebConfig 가 아니라 여기 있는 이유: 스프링 설정이 아니라 순수 계산이라 @Configuration 안에 둘 이유가 없다.
 */
public final class LocaleUtil {

    public static final String DEFAULT_LOCALE = "ko";
    private static final List<String> SUPPORTED = List.of("ko", "en", "ja", "zh");

    private LocaleUtil() {
    }

    /** Accept-Language 에서 지원 언어 하나를 고른다. 없거나 모르는 언어면 ko. */
    public static String resolve(String acceptLanguage) {
        return orDefault(match(acceptLanguage));
    }

    /**
     * 쿼리 locale 을 먼저 보고, 없으면 Accept-Language 를 본다.
     * 둘 다 지원 언어를 가리키지 않으면 null — 호출한 Service 가 users.locale 로 이어서 판단한다.
     */
    public static String hint(String queryLocale, String acceptLanguage) {
        String fromQuery = match(queryLocale);
        return fromQuery != null ? fromQuery : match(acceptLanguage);
    }

    /** null 이면 ko. Service 가 users.locale 까지 확인한 뒤 마지막에 부른다. */
    public static String orDefault(String locale) {
        return locale == null ? DEFAULT_LOCALE : locale;
    }

    /**
     * 지원 언어면 그 값을, 아니면 null.
     * "en-US,en;q=0.9" 처럼 여러 개가 오면 첫 번째만 본다 — q 값까지 해석할 만큼 언어가 많지 않다.
     */
    private static String match(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String primary = raw.split(",")[0].trim().toLowerCase(Locale.ROOT);
        return SUPPORTED.stream()
                .filter(primary::startsWith)
                .findFirst()
                .orElse(null);
    }
}
