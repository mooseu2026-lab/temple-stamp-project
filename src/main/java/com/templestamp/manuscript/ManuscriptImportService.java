package com.templestamp.manuscript;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import com.templestamp.global.config.ManuscriptProperties;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.manuscript.dto.ImportError;
import com.templestamp.manuscript.dto.ImportResultResponse;
import com.templestamp.site.SiteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV 일괄 반입(챕터 8 §4). 원고 140편이 들어오는 통로다.
 * <p>
 * <b>파일 단위 원자성</b>이 이 클래스의 전부다. 한 행이라도 틀리면 아무것도 넣지 않는다 —
 * 절반만 들어간 파일은 "어디까지 들어갔는지" 를 사람이 세어야 하고, 다시 올리면 중복이 난다.
 * 그래서 검증을 먼저 전부 돌고, 오류가 하나라도 있으면 400 과 함께 행 번호를 돌려준다.
 * <p>
 * 사찰 매칭은 <b>이름 완전 일치 + 시군구 대조</b>다. 이 프로젝트에는 같은 이름의 절이
 * 7이름 15곳 있어서(정리.md §6-9), 이름만으로 맞추면 다른 고장의 절에 원고가 붙는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManuscriptImportService {

    private static final String[] HEADER = {"site_name", "sigungu", "verse_no", "kind", "title", "body"};
    private static final int MAX_ERRORS = 50;
    private static final char BOM = '﻿';

    private final ManuscriptMapper manuscriptMapper;
    private final ManuscriptService manuscriptService;
    private final ManuscriptProperties manuscriptProperties;
    private final SiteMapper siteMapper;

    /**
     * @param dryRun 참이면 검증만 하고 한 줄도 넣지 않는다. 140편을 올리기 전에 먼저 이걸 돌린다.
     */
    @Transactional
    public ImportResultResponse importCsv(Long adminId, MultipartFile file, boolean dryRun) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.COMMON_4000, "CSV 파일을 첨부해주세요.");
        }
        if (file.getSize() > manuscriptProperties.importMaxBytes()) {
            throw new BusinessException(ErrorCode.COMMON_4130,
                    "파일이 너무 큽니다 — %d MB 까지 올릴 수 있습니다."
                            .formatted(manuscriptProperties.importMaxBytes() / (1024 * 1024)));
        }

        List<String[]> rows = read(file);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.COMMON_4000, "머리글만 있고 내용이 없습니다.");
        }
        if (rows.size() > manuscriptProperties.importMaxRows()) {
            throw new BusinessException(ErrorCode.COMMON_4000,
                    "한 번에 %d행까지 올릴 수 있습니다.".formatted(manuscriptProperties.importMaxRows()));
        }

        List<ImportError> errors = new ArrayList<>();
        List<Manuscript> ready = new ArrayList<>();
        // 파일 안에서도 같은 자리에 여러 행이 올 수 있다. DB 의 현재 수에 파일 안의 수를 더해서 본다.
        Map<String, Integer> pendingPerKey = new HashMap<>();

        for (int i = 0; i < rows.size(); i++) {
            int rowNo = i + 2;   // 머리글이 1행이므로 사람이 보는 번호는 +2
            try {
                ready.add(toManuscript(adminId, rows.get(i), pendingPerKey));
            } catch (RowError e) {
                if (errors.size() < MAX_ERRORS) {
                    errors.add(new ImportError(rowNo, e.field, e.getMessage()));
                }
            }
        }

        if (!errors.isEmpty()) {
            // 한 행이라도 틀리면 아무것도 넣지 않는다. 여기서 예외를 던져 트랜잭션도 함께 접는다.
            throw new ImportFailedException(errors);
        }
        if (dryRun) {
            log.info("CSV 드라이런. adminId={}, 넣을 수 있는 행={}", adminId, ready.size());
            return new ImportResultResponse(true, ready.size(), 0, List.of());
        }

        ready.forEach(manuscriptMapper::insert);
        log.info("CSV 반입. adminId={}, 삽입={}", adminId, ready.size());
        return new ImportResultResponse(true, ready.size(), ready.size(), List.of());
    }

    /* ---------------- 내부 ---------------- */

    private List<String[]> read(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVReader csv = new CSVReader(reader)) {

            String[] header = csv.readNext();
            if (header == null) {
                throw new BusinessException(ErrorCode.COMMON_4000, "빈 파일입니다.");
            }
            // 엑셀이 저장한 UTF-8 파일은 맨 앞에 BOM 이 붙는다. 그것 때문에 첫 열 이름이 안 맞는 일이 흔하다.
            if (header.length > 0 && !header[0].isEmpty() && header[0].charAt(0) == BOM) {
                header[0] = header[0].substring(1);
            }
            for (int i = 0; i < HEADER.length; i++) {
                if (header.length <= i || !HEADER[i].equalsIgnoreCase(header[i].trim())) {
                    throw new BusinessException(ErrorCode.COMMON_4000,
                            "머리글이 다릅니다. 첫 줄은 %s 여야 합니다.".formatted(String.join(",", HEADER)));
                }
            }
            List<String[]> rows = new ArrayList<>();
            String[] line;
            while ((line = csv.readNext()) != null) {
                if (line.length == 1 && line[0].isBlank()) {
                    continue;   // 끝의 빈 줄
                }
                rows.add(line);
            }
            return rows;
        } catch (IOException | CsvValidationException e) {
            throw new BusinessException(ErrorCode.COMMON_4000, "CSV 를 읽을 수 없습니다: " + e.getMessage());
        }
    }

    private Manuscript toManuscript(Long adminId, String[] row, Map<String, Integer> pendingPerKey) {
        String siteName = col(row, 0);
        String sigungu = col(row, 1);
        String verseRaw = col(row, 2);
        String kind = col(row, 3).toUpperCase();
        String title = ManuscriptService.clean(col(row, 4));
        String body = ManuscriptService.clean(col(row, 5));

        int verseNo;
        try {
            verseNo = Integer.parseInt(verseRaw);
        } catch (NumberFormatException e) {
            throw new RowError("verse_no", "구절 번호는 1~5 의 숫자여야 합니다.");
        }
        if (verseNo < 1 || verseNo > 5) {
            throw new RowError("verse_no", "구절 번호는 1~5 입니다.");
        }
        if (!Manuscript.MISSION.equals(kind) && !Manuscript.EXT.equals(kind)) {
            throw new RowError("kind", "종류는 MISSION 또는 EXT 입니다.");
        }

        Long siteId = null;
        if (!siteName.isBlank()) {
            // 이름만으로 찾지 않는다 — 같은 이름의 절이 여러 고장에 있다(정리.md §6-9).
            List<Long> matched = siteMapper.findIdsByNameAndSigungu(siteName, sigungu);
            if (matched.isEmpty()) {
                throw new RowError("site_name", "이름과 시군구가 모두 맞는 사찰이 없습니다: %s(%s)"
                        .formatted(siteName, sigungu));
            }
            if (matched.size() > 1) {
                throw new RowError("site_name", "같은 이름·시군구의 사찰이 여럿입니다 — 시군구를 더 정확히 적어주세요.");
            }
            siteId = matched.get(0);
        }

        try {
            manuscriptService.validateContent(kind, title, body);
        } catch (BusinessException e) {
            throw new RowError(ErrorCode.MS_4002.equals(e.getErrorCode()) ? "body" : "title", e.getMessage());
        }

        long siteKey = siteId == null ? 0L : siteId;
        if (manuscriptMapper.countSameBody(siteKey, verseNo, kind, body) > 0) {
            throw new RowError("body", "같은 내용의 원고가 이미 있습니다.");
        }

        String key = siteKey + ":" + verseNo + ":" + kind;
        int inFile = pendingPerKey.getOrDefault(key, 0);
        int max = siteId == null ? 1 : manuscriptProperties.maxVariants();
        if (manuscriptMapper.countActive(siteKey, verseNo, kind) + inFile >= max) {
            throw new RowError("body", "변형 상한(%d)을 넘습니다 — 파일 안의 같은 자리 행까지 함께 셉니다.".formatted(max));
        }
        pendingPerKey.put(key, inFile + 1);

        return Manuscript.builder()
                .siteId(siteId)
                .verseNo(verseNo)
                .kind(kind)
                .variantNo(manuscriptMapper.maxVariantNo(siteKey, verseNo, kind) + 1 + inFile)
                .status(Manuscript.DRAFT)   // 반입은 전부 초안이다. 심사는 따로 받는다
                .title(title)
                .body(body)
                .authorId(adminId)
                .build();
    }

    private static String col(String[] row, int index) {
        return row.length > index && row[index] != null ? row[index].trim() : "";
    }

    /** 행 하나의 실패. 파일 전체를 접기 전에 모아 두려고 예외로 나른다. */
    private static class RowError extends RuntimeException {
        private final String field;

        RowError(String field, String message) {
            super(message);
            this.field = field;
        }
    }

    /** 검증에서 걸린 행 목록을 그대로 응답에 싣기 위한 예외. GlobalExceptionHandler 가 400 으로 바꾼다. */
    public static class ImportFailedException extends RuntimeException {
        private final transient List<ImportError> errors;

        ImportFailedException(List<ImportError> errors) {
            super("CSV 검증 실패 %d건".formatted(errors.size()));
            this.errors = errors;
        }

        public List<ImportError> errors() {
            return errors;
        }
    }
}
