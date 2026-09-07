// src/main/java/com/templestamp/content/seed/SeedCsv.java
package com.templestamp.content.seed;

import com.opencsv.CSVReaderHeaderAware;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UTF-8(BOM 허용) CSV → 헤더 기반 Map 목록.
 * <p>
 * 값 정리 규칙이 핵심이다. 조사 원본은 "모름" 을 빈 칸·{@code ?}·"미확인…" 세 가지로 적었다.
 * 그대로 넣으면 화면에 "미확인" 이라는 글자가 안내문처럼 뜬다 — 셋 다 {@code null} 로 읽어
 * 시드에 넣지 않고, 관리자가 나중에 채우게 둔다.
 */
final class SeedCsv {

    static List<Map<String, String>> read(String classpath) throws Exception {
        try (var reader = new CSVReaderHeaderAware(
                new InputStreamReader(new ClassPathResource(classpath).getInputStream(), StandardCharsets.UTF_8))) {
            List<Map<String, String>> out = new ArrayList<>();
            Map<String, String> row;
            while ((row = reader.readMap()) != null) {
                Map<String, String> cleaned = new LinkedHashMap<>();
                // 첫 헤더에 BOM(﻿)이 붙어 오면 "region_code" 를 못 찾아 전 행이 null 이 된다.
                row.forEach((k, v) -> cleaned.put(k.replace("﻿", "").trim(), clean(v)));
                out.add(cleaned);
            }
            return out;
        }
    }

    static String clean(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        return (v.isEmpty() || v.equals("?") || v.startsWith("미확인")) ? null : v;
    }

    static boolean yes(String value) {
        return "Y".equalsIgnoreCase(value);
    }

    private SeedCsv() {
    }
}
