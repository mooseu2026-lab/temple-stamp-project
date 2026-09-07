package com.templestamp.manuscript;

import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.manuscript.dto.ImportResultResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CSV 일괄 반입(챕터 8 §4·§7-2). 원고 140편이 들어오는 통로다.
 * <p>
 * 이 클래스가 지키는 성질은 하나다 — <b>파일 단위 원자성</b>. 절반만 들어간 파일은
 * "어디까지 들어갔는지" 를 사람이 세어야 하고, 다시 올리면 중복이 난다.
 * 그래서 여기 검사는 대부분 "실패했을 때 <em>몇 줄이 남았는가</em>" 를 센다.
 */
@SpringBootTest
class ManuscriptImportTest {

    private static final long ADM = 1L;
    private static final String HEADER = "site_name,sigungu,verse_no,kind,title,body";
    private static final char BOM = '﻿';

    @Autowired ManuscriptImportService manuscriptImportService;
    @Autowired JdbcTemplate jdbc;

    /**
     * 이 검사가 넣은 행만 세고 이 검사가 넣은 행만 지운다.
     * "시더의 열 편보다 뒤 번호" 로 세면 폴더 N 이 남긴 행까지 함께 세어, 검사가 다른 검사의
     * 뒤치다꺼리에 따라 붙었다 떨어졌다 한다 — 실제로 그렇게 무너진 적이 있다.
     */
    private long baseId;

    @BeforeEach
    void setUp() {
        // 이 검사가 쓰는 사찰에 다른 검증(N 폴더 CSV 반입)이 남긴 원고가 있으면
        // 상한 계산에 함께 세어져 "몇 행이 걸리는가" 가 흔들린다. 그 자리만 비우고 시작한다.
        // 시더의 기본 원고 열 편은 건드리지 않는다.
        jdbc.update("DELETE FROM manuscript WHERE manuscript_id > 10 AND site_id IN "
                + "(SELECT site_id FROM site WHERE name IN ('대원사','백련사','송광사','용문사','흥국사','천은사'))");
        baseId = jdbc.queryForObject("SELECT COALESCE(MAX(manuscript_id), 0) FROM manuscript", Long.class);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM manuscript WHERE manuscript_id > ?", baseId);
    }

    @Test
    @DisplayName("① 한 행이 틀리면 아무것도 넣지 않는다 — 절반 삽입은 다음 반입을 못 믿게 만든다")
    void oneBadRowRollsBackTheWholeFile() {
        String csv = HEADER + "\n"
                + row("대원사", "산청", 1, "MISSION", "괜찮은 행", body("가")) + "\n"
                + row("없는절", "없는곳", 1, "MISSION", "틀린 행", body("나")) + "\n"
                + row("백련사", "강진", 2, "MISSION", "또 괜찮은 행", body("다")) + "\n";

        assertThatThrownBy(() -> importCsv(csv, false))
                .isInstanceOf(ManuscriptImportService.ImportFailedException.class);
        assertThat(inserted()).as("한 줄도 들어가지 않았다").isZero();
    }

    @Test
    @DisplayName("② 드라이런은 세어만 준다 — 140편을 올리기 전에 돌리라는 뜻이다")
    void dryRunInsertsNothing() {
        String csv = HEADER + "\n"
                + row("대원사", "산청", 1, "MISSION", "드라이런 하나", body("라")) + "\n"
                + row("백련사", "강진", 2, "MISSION", "드라이런 둘", body("마")) + "\n";

        ImportResultResponse result = importCsv(csv, true);
        assertThat(result.ok()).isTrue();
        assertThat(result.wouldInsert()).isEqualTo(2);
        assertThat(result.inserted()).isZero();
        assertThat(inserted()).isZero();
    }

    @Test
    @DisplayName("③ 상한은 파일 안의 행끼리도 합산한다 — 같은 키 4행이면 4행째가 걸린다")
    void capCountsRowsInsideTheFile() {
        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        for (int i = 1; i <= 4; i++) {
            csv.append(row("대원사", "산청", 1, "MISSION", "변형 " + i, body("변형 " + i))).append('\n');
        }
        assertThatThrownBy(() -> importCsv(csv.toString(), false))
                .isInstanceOf(ManuscriptImportService.ImportFailedException.class)
                .satisfies(e -> {
                    var errors = ((ManuscriptImportService.ImportFailedException) e).errors();
                    assertThat(errors).hasSize(1);
                    assertThat(errors.get(0).row()).as("머리글이 1행이므로 네 번째 행은 5행").isEqualTo(5);
                    assertThat(errors.get(0).reason()).contains("상한");
                });
        assertThat(inserted()).isZero();
    }

    @Test
    @DisplayName("④ 동명이찰 — 이름만으로 맞추지 않는다. 산청 대원사와 보성 대원사는 다른 절이다")
    void sameNameDifferentDistrict() {
        String csv = HEADER + "\n"
                + row("대원사", "산청", 1, "MISSION", "산청 쪽", body("산청")) + "\n"
                + row("대원사", "보성", 1, "MISSION", "보성 쪽", body("보성")) + "\n";

        ImportResultResponse result = importCsv(csv, false);
        assertThat(result.inserted()).isEqualTo(2);

        Integer distinctSites = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT site_id) FROM manuscript WHERE manuscript_id > ?", Integer.class, baseId);
        assertThat(distinctSites).as("두 행이 서로 다른 절에 붙었다").isEqualTo(2);
    }

    @Test
    @DisplayName("⑤ 이름과 시군구가 함께 맞아야 한다 — 산청 백련사는 없다")
    void wrongDistrictDoesNotMatch() {
        String csv = HEADER + "\n"
                + row("백련사", "산청", 1, "MISSION", "엉뚱한 고장", body("엉뚱")) + "\n";
        assertThatThrownBy(() -> importCsv(csv, false))
                .isInstanceOf(ManuscriptImportService.ImportFailedException.class)
                .satisfies(e -> assertThat(((ManuscriptImportService.ImportFailedException) e).errors().get(0).field())
                        .isEqualTo("site_name"));
    }

    @Test
    @DisplayName("⑥ 엑셀이 붙이는 BOM 이 있어도 머리글을 읽는다")
    void bomHeaderIsAccepted() {
        String csv = BOM + HEADER + "\n"
                + row("대원사", "산청", 1, "MISSION", "BOM 붙은 파일", body("BOM")) + "\n";
        assertThat(importCsv(csv, false).inserted()).isEqualTo(1);
    }

    @Test
    @DisplayName("⑦ 머리글이 다르면 첫 줄에서 멈춘다 — 열 순서가 밀린 파일을 반쯤 읽지 않는다")
    void wrongHeaderStopsEarly() {
        String csv = "temple,gu,verse,kind,title,body\n"
                + row("대원사", "산청", 1, "MISSION", "머리글 틀림", body("머리글")) + "\n";
        assertThatThrownBy(() -> importCsv(csv, false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.COMMON_4000);
    }

    @Test
    @DisplayName("⑧ 행 상한 1,000 — 넘으면 한 줄도 읽지 않는다")
    void tooManyRows() {
        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        for (int i = 0; i < 1001; i++) {
            csv.append(row("대원사", "산청", 1, "MISSION", "많음 " + i, body("많음 " + i))).append('\n');
        }
        assertThatThrownBy(() -> importCsv(csv.toString(), true))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.COMMON_4000);
        assertThat(inserted()).isZero();
    }

    @Test
    @DisplayName("⑨ 파일 크기 상한 2 MB — 크기는 읽기 전에 본다")
    void tooBig() {
        byte[] big = new byte[2 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("file", "big.csv", "text/csv", big);
        assertThatThrownBy(() -> manuscriptImportService.importCsv(ADM, file, true))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.COMMON_4130);
    }

    @Test
    @DisplayName("⑩ 반입본은 전부 DRAFT 다 — 반입이 곧 게시가 되면 심사라는 단계가 없어진다")
    void importedRowsAreDrafts() {
        String csv = HEADER + "\n"
                + row("대원사", "산청", 1, "MISSION", "초안으로", body("초안")) + "\n";
        importCsv(csv, false);
        String status = jdbc.queryForObject(
                "SELECT status FROM manuscript WHERE manuscript_id > ? LIMIT 1", String.class, baseId);
        assertThat(status).isEqualTo(Manuscript.DRAFT);
    }

    /* ---------------- 도구 ---------------- */

    private ImportResultResponse importCsv(String content, boolean dryRun) {
        MockMultipartFile file = new MockMultipartFile(
                "file", "manuscripts.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
        return manuscriptImportService.importCsv(ADM, file, dryRun);
    }

    private String row(String siteName, String sigungu, int verseNo, String kind, String title, String bodyText) {
        return String.join(",", siteName, sigungu, String.valueOf(verseNo), kind, title, "\"" + bodyText + "\"");
    }

    private String body(String mark) {
        return "여기에 담는 글은 " + mark + " 입니다. 본문이 서로 달라야 중복 규칙에 걸리지 않습니다.";
    }

    private int inserted() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM manuscript WHERE manuscript_id > ?", Integer.class, baseId);
    }
}
