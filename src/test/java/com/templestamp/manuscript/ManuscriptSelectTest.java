package com.templestamp.manuscript;

import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 원고 선택 규칙(챕터 8 §3-1·§7-2).
 * <p>
 * 규칙은 한 줄이다 — <b>후보[userId mod 후보 수]</b>. 난수가 아니라 나머지 연산인 이유는
 * 같은 사람이 같은 사찰에서 늘 같은 글을 보아야 하기 때문이다. 화면을 새로 고칠 때마다 글이 바뀌면
 * 사용자는 읽던 문장을 잃는다.
 * <p>
 * "세션 고정" 은 이 선택기가 하는 일이 아니다 — 도장 행에 적어 둔 번호를 다시 읽는 것이 고정이라,
 * 여기서는 그 번호로 되찾아 오는 길({@code findById})이 퇴역 뒤에도 살아 있는지를 본다.
 */
@SpringBootTest
class ManuscriptSelectTest {

    private static final long ED1 = 4L;
    private static final int VERSE = 3;

    @Autowired ManuscriptSelector manuscriptSelector;
    @Autowired JdbcTemplate jdbc;

    private long siteId;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO site (name, latitude, longitude, verify_radius, qr_version, status) "
                + "VALUES ('검증사찰선택', 37.5, 127.0, 150, 1, 'ACTIVE')");
        siteId = jdbc.queryForObject("SELECT MAX(site_id) FROM site WHERE name = '검증사찰선택'", Long.class);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM manuscript WHERE site_id = ?", siteId);
        jdbc.update("DELETE FROM site WHERE site_id = ?", siteId);
    }

    @Test
    @DisplayName("① userId mod 후보 수 — 같은 사람은 몇 번을 물어도 같은 원고")
    void deterministicByUserId() {
        long v1 = approved(1, "첫 변형");
        long v2 = approved(2, "둘째 변형");
        long v3 = approved(3, "셋째 변형");
        long[] order = {v1, v2, v3};

        for (long userId = 1; userId <= 12; userId++) {
            Manuscript picked = manuscriptSelector.pickMission(userId, siteId, VERSE);
            assertThat(picked.getManuscriptId())
                    .as("userId " + userId)
                    .isEqualTo(order[(int) (userId % 3)]);
            // 두 번 물어도 같아야 한다
            assertThat(manuscriptSelector.pickMission(userId, siteId, VERSE).getManuscriptId())
                    .isEqualTo(picked.getManuscriptId());
        }
    }

    @Test
    @DisplayName("② 사찰 원고가 없으면 기본 원고로 — 사람마다 다르게가 아니라 있는 것으로")
    void fallsBackToDefault() {
        Manuscript picked = manuscriptSelector.pickMission(7L, siteId, VERSE);
        assertThat(picked.getSiteId()).as("기본 원고다").isNull();
        assertThat(picked.getStatus()).isEqualTo(Manuscript.APPROVED);
    }

    @Test
    @DisplayName("③ 승인본만 후보다 — 초안·제출·반려·퇴역은 새로 나가지 않는다")
    void onlyApprovedAreCandidates() {
        long draft = insert(1, Manuscript.DRAFT, "초안");
        long submitted = insert(2, Manuscript.SUBMITTED, "제출");
        long rejected = insert(3, Manuscript.REJECTED, "반려");

        Manuscript picked = manuscriptSelector.pickMission(5L, siteId, VERSE);
        assertThat(picked.getManuscriptId()).isNotIn(draft, submitted, rejected);
        assertThat(picked.getSiteId()).as("고를 승인본이 없으니 기본 원고로 간다").isNull();
    }

    @Test
    @DisplayName("④ 세션 고정 — 퇴역한 원고도 번호로는 되찾아 온다(읽던 글이 사라지지 않는다)")
    void retiredStillReadableById() {
        long only = approved(1, "이 글을 읽는 중이다");
        Manuscript picked = manuscriptSelector.pickMission(9L, siteId, VERSE);
        assertThat(picked.getManuscriptId()).isEqualTo(only);

        jdbc.update("UPDATE manuscript SET status = 'RETIRED', retired_at = NOW() WHERE manuscript_id = ?", only);

        // 새로 고르면 더 이상 후보가 아니다
        assertThat(manuscriptSelector.pickMission(9L, siteId, VERSE).getSiteId()).isNull();
        // 그러나 도장이 적어 둔 번호로는 그대로 읽힌다 — 이것이 세션 고정의 실체다
        Manuscript kept = manuscriptSelector.findById(only);
        assertThat(kept).isNotNull();
        assertThat(kept.getBody()).isEqualTo(body("이 글을 읽는 중이다"));
    }

    @Test
    @DisplayName("⑤ 확장문구는 없어도 된다 — 없으면 null 이지 예외가 아니다")
    void extIsOptional() {
        // 기본 EXT 는 시더가 넣어 두므로, 사찰 전용이 없을 때 기본으로 내려오는지까지 본다.
        Manuscript ext = manuscriptSelector.pickExt(3L, siteId, VERSE);
        assertThat(ext).isNotNull();
        assertThat(ext.getSiteId()).isNull();
        assertThat(ext.getKind()).isEqualTo(Manuscript.EXT);
    }

    @Test
    @DisplayName("⑥ 기본 원고까지 없으면 MS-4093 — 500 이 아니라 '아직 준비되지 않았다'")
    void missingEverythingIsConflictNotCrash() {
        Long defaultId = jdbc.queryForObject(
                "SELECT manuscript_id FROM manuscript WHERE site_id IS NULL AND verse_no = ? "
                        + "AND kind = 'MISSION' AND status = 'APPROVED' LIMIT 1", Long.class, VERSE);
        jdbc.update("UPDATE manuscript SET status = 'RETIRED', retired_at = NOW() WHERE manuscript_id = ?", defaultId);
        try {
            assertThatThrownBy(() -> manuscriptSelector.pickMission(2L, siteId, VERSE))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.MS_4093);
        } finally {
            // 시더 상태로 되돌린다 — 이 한 줄을 빼면 H15 와 폴더 N 이 함께 무너진다.
            jdbc.update("UPDATE manuscript SET status = 'APPROVED', retired_at = NULL WHERE manuscript_id = ?", defaultId);
        }
    }

    /* ---------------- 도구 ---------------- */

    private long approved(int variantNo, String mark) {
        return insert(variantNo, Manuscript.APPROVED, mark);
    }

    private long insert(int variantNo, String status, String mark) {
        jdbc.update("INSERT INTO manuscript (site_id, verse_no, kind, variant_no, status, title, body, author_id) "
                        + "VALUES (?, ?, 'MISSION', ?, ?, ?, ?, ?)",
                siteId, VERSE, variantNo, status, mark, body(mark), ED1);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private String body(String mark) {
        return "여기에 담는 글은 " + mark + " 입니다. 본문이 서로 달라야 중복 규칙에 걸리지 않습니다.";
    }
}
