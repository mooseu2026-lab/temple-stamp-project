package com.templestamp.manuscript;

import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.manuscript.dto.ManuscriptCreateRequest;
import com.templestamp.manuscript.dto.ManuscriptResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 원고 등록·수정·심사(챕터 8 §2·§7-2).
 * <p>
 * 이 표의 핵심은 상태가 아니라 <b>상태 사이의 길</b>이다. 어떤 상태에 있느냐보다 그 상태에서
 * 무엇을 할 수 있느냐가 규칙이고, 열려 있지 않은 길은 전부 같은 답(MS-4090)을 줘야 한다 —
 * 답이 갈리면 밖에서 내부 상태를 되짚을 수 있다.
 */
@SpringBootTest
class ManuscriptServiceTest {

    private static final long ED1 = 4L;      // 시더의 편집자
    private static final long ADM = 1L;      // 시더의 관리자
    private static final int VERSE = 4;

    @Autowired ManuscriptService manuscriptService;
    @Autowired ManuscriptMapper manuscriptMapper;
    @Autowired JdbcTemplate jdbc;

    private long siteId;

    @BeforeEach
    void setUp() {
        // 전용 사찰을 하나 세운다. 시드 사찰에 붙이면 컬렉션·다른 검사와 같은 자리를 다투게 된다.
        jdbc.update("INSERT INTO site (name, latitude, longitude, verify_radius, qr_version, status) "
                + "VALUES ('검증사찰원고', 37.5, 127.0, 150, 1, 'ACTIVE')");
        siteId = jdbc.queryForObject("SELECT MAX(site_id) FROM site WHERE name = '검증사찰원고'", Long.class);
    }

    @AfterEach
    void tearDown() {
        // 원고가 먼저다 — site 를 먼저 지우면 FK 가 막는다.
        jdbc.update("DELETE FROM manuscript WHERE site_id = ?", siteId);
        jdbc.update("DELETE FROM site WHERE site_id = ?", siteId);
    }

    /* ---------------- 상태 전이 ---------------- */

    @Test
    @DisplayName("① 열린 길 일곱 — 수정·제출·반려·되수정·승인·퇴역·기본 원고 교체")
    void allowedTransitions() {
        ManuscriptResponse m = create("첫 원고", "일주문 앞에서 걸음을 멈추고 오늘 여기에 온 까닭을 적어 봅니다.");

        // 1) DRAFT → DRAFT (수정)
        ManuscriptResponse edited = manuscriptService.update(ED1, m.manuscriptId(), "고친 제목", body("가"));
        assertThat(edited.status()).isEqualTo(Manuscript.DRAFT);

        // 2) DRAFT → SUBMITTED
        manuscriptService.submit(ED1, m.manuscriptId());
        assertThat(status(m.manuscriptId())).isEqualTo(Manuscript.SUBMITTED);

        // 3) SUBMITTED → REJECTED
        manuscriptService.reject(ADM, m.manuscriptId(), "문장을 줄여 주세요.");
        assertThat(status(m.manuscriptId())).isEqualTo(Manuscript.REJECTED);

        // 4) REJECTED → DRAFT (되수정) — 사유는 이력으로 남는다
        manuscriptService.update(ED1, m.manuscriptId(), "다시 고친 제목", body("나"));
        assertThat(status(m.manuscriptId())).isEqualTo(Manuscript.DRAFT);
        assertThat(manuscriptMapper.findById(m.manuscriptId()).orElseThrow().getRejectReason()).isNotBlank();

        // 5) DRAFT → SUBMITTED → APPROVED
        manuscriptService.submit(ED1, m.manuscriptId());
        manuscriptService.approve(ADM, m.manuscriptId());
        assertThat(status(m.manuscriptId())).isEqualTo(Manuscript.APPROVED);

        // 6) APPROVED → RETIRED
        manuscriptService.retire(ADM, m.manuscriptId());
        assertThat(status(m.manuscriptId())).isEqualTo(Manuscript.RETIRED);

        // 7) 기본 원고 교체 — 새것이 서면 옛것이 물러난다
        Long oldDefault = jdbc.queryForObject(
                "SELECT manuscript_id FROM manuscript WHERE site_id IS NULL AND verse_no = ? "
                        + "AND kind = 'MISSION' AND status = 'APPROVED' LIMIT 1", Long.class, VERSE);
        long fresh = insertRaw(null, VERSE, Manuscript.MISSION, 99, Manuscript.SUBMITTED, body("기본 교체"));
        manuscriptService.approve(ADM, fresh);
        assertThat(status(oldDefault)).isEqualTo(Manuscript.RETIRED);
        assertThat(status(fresh)).isEqualTo(Manuscript.APPROVED);

        // 시더 상태로 되돌린다 — 다른 검사가 기본 원고 다섯 편을 전제한다(H15).
        jdbc.update("DELETE FROM manuscript WHERE manuscript_id = ?", fresh);
        jdbc.update("UPDATE manuscript SET status = 'APPROVED', retired_at = NULL WHERE manuscript_id = ?", oldDefault);
    }

    @Test
    @DisplayName("② 닫힌 길은 전부 같은 답 MS-4090 — 답이 갈리면 내부 상태가 새어 나간다")
    void deniedTransitionsAllReturn4090() {
        ManuscriptResponse m = create("닫힌 길", "여기에서 갈 수 없는 길을 하나씩 두드려 봅니다.");
        long id = m.manuscriptId();

        // DRAFT 에서 막히는 것
        deny(() -> manuscriptService.approve(ADM, id));
        deny(() -> manuscriptService.reject(ADM, id, "사유"));
        deny(() -> manuscriptService.retire(ADM, id));

        manuscriptService.submit(ED1, id);
        // SUBMITTED 에서 막히는 것
        deny(() -> manuscriptService.submit(ED1, id));
        deny(() -> manuscriptService.update(ED1, id, "제목", body("다")));
        deny(() -> manuscriptService.retire(ADM, id));

        manuscriptService.approve(ADM, id);
        // APPROVED 에서 막히는 것
        deny(() -> manuscriptService.submit(ED1, id));
        deny(() -> manuscriptService.update(ED1, id, "제목", body("라")));
        deny(() -> manuscriptService.approve(ADM, id));
        deny(() -> manuscriptService.reject(ADM, id, "사유"));

        manuscriptService.retire(ADM, id);
        // RETIRED 는 끝이다 — 여기서 나가는 길은 없다
        deny(() -> manuscriptService.submit(ED1, id));
        deny(() -> manuscriptService.update(ED1, id, "제목", body("마")));
        deny(() -> manuscriptService.approve(ADM, id));
        deny(() -> manuscriptService.reject(ADM, id, "사유"));
        deny(() -> manuscriptService.retire(ADM, id));
    }

    @Test
    @DisplayName("③ 4-eyes — 자기 원고는 자기가 승인도 반려도 못 한다")
    void fourEyes() {
        ManuscriptResponse mine = create(ADM, "관리자가 쓴 글", body("바"));
        manuscriptService.submit(ADM, mine.manuscriptId());

        assertThatThrownBy(() -> manuscriptService.approve(ADM, mine.manuscriptId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.MS_4030);
        assertThatThrownBy(() -> manuscriptService.reject(ADM, mine.manuscriptId(), "사유"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.MS_4030);
    }

    @Test
    @DisplayName("④ 남의 원고는 '없다' 고 답한다 — 있다는 사실 자체가 정보다")
    void othersManuscriptLooksMissing() {
        ManuscriptResponse mine = create("남의 글", body("사"));
        assertThatThrownBy(() -> manuscriptService.update(ADM, mine.manuscriptId(), "가로채기", body("아")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.MS_4040);
    }

    /* ---------------- 내용 규칙 ---------------- */

    @Test
    @DisplayName("⑤ 금칙 네 가지 — 링크·전화·메일·태그는 지우지 않고 거부한다")
    void forbiddenPatterns() {
        assertMs4002("링크", "자세한 것은 https://example.com 을 보세요. 그림도 함께 실려 있습니다.");
        assertMs4002("전화", "문의는 010-1234-5678 로 연락 주세요. 낮에는 받지 못합니다.");
        assertMs4002("메일", "궁금하면 monk@example.com 으로 보내 주세요. 답이 늦을 수 있습니다.");
        assertMs4002("태그", "여기에 <b>굵은 글씨</b>를 넣어 보았습니다. 화면에서는 그대로 보입니다.");
    }

    @Test
    @DisplayName("⑥ 같은 본문은 두 번 들어가지 않는다 MS-4091")
    void duplicateBody() {
        String same = "같은 본문을 두 번 넣으면 읽는 사람이 둘을 구별할 수 없습니다.";
        create("먼저", same);
        assertThatThrownBy(() -> create("나중", same))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.MS_4091);
    }

    @Test
    @DisplayName("⑦ 변형 상한 3 — 네 번째는 MS-4092")
    void variantCap() {
        create("하나", body("하나"));
        create("둘", body("둘"));
        create("셋", body("셋"));
        assertThatThrownBy(() -> create("넷", body("넷")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.MS_4092);
    }

    @Test
    @DisplayName("⑧ 기본 원고는 구·종류마다 한 편 — 시더가 채운 자리에 둘째는 못 선다")
    void defaultIsSingle() {
        assertThatThrownBy(() -> manuscriptService.create(ED1, new ManuscriptCreateRequest(
                null, VERSE, Manuscript.MISSION, "기본 둘째", body("기본 둘째"))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.MS_4092);
    }

    /* ---------------- 동시성 ---------------- */

    @Test
    @DisplayName("⑨ 여섯 스레드가 같은 자리에 동시 등록 — 번호 중복 0 · 상한 초과 0")
    void concurrentCreateKeepsNumbering() throws Exception {
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger created = new AtomicInteger();
        List<String> unexpected = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final int n = i;
            pool.submit(() -> {
                try {
                    start.await();
                    manuscriptService.create(ED1, new ManuscriptCreateRequest(
                            siteId, VERSE, Manuscript.MISSION, "동시 " + n, body("동시 " + n)));
                    created.incrementAndGet();
                } catch (BusinessException e) {
                    // 상한(4092)만 정상적인 실패다. 그 밖의 실패는 잠금이 새고 있다는 뜻이다.
                    if (e.getErrorCode() != ErrorCode.MS_4092) {
                        synchronized (unexpected) { unexpected.add(e.getErrorCode().name()); }
                    }
                } catch (Exception e) {
                    synchronized (unexpected) { unexpected.add(e.getClass().getSimpleName()); }
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(unexpected).as("상한 말고 다른 이유로 실패하면 안 된다").isEmpty();
        assertThat(created.get()).as("상한을 넘겨 들어간 것이 없다").isLessThanOrEqualTo(3);

        Integer distinct = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT variant_no) FROM manuscript WHERE site_id = ? AND verse_no = ? AND kind = 'MISSION'",
                Integer.class, siteId, VERSE);
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM manuscript WHERE site_id = ? AND verse_no = ? AND kind = 'MISSION'",
                Integer.class, siteId, VERSE);
        assertThat(distinct).as("번호가 겹치지 않는다").isEqualTo(rows);
        assertThat(rows).isEqualTo(created.get());
    }

    /* ---------------- 도구 ---------------- */

    private ManuscriptResponse create(String title, String bodyText) {
        return create(ED1, title, bodyText);
    }

    private ManuscriptResponse create(long authorId, String title, String bodyText) {
        return manuscriptService.create(authorId,
                new ManuscriptCreateRequest(siteId, VERSE, Manuscript.MISSION, title, bodyText));
    }

    private void assertMs4002(String label, String bodyText) {
        assertThatThrownBy(() -> create("금칙 " + label, bodyText))
                .as(label)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.MS_4002);
    }

    private void deny(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.MS_4090);
    }

    private String status(Long id) {
        return jdbc.queryForObject("SELECT status FROM manuscript WHERE manuscript_id = ?", String.class, id);
    }

    private String body(String mark) {
        return "여기에 담는 글은 " + mark + " 입니다. 본문이 서로 달라야 중복 규칙에 걸리지 않습니다.";
    }

    private long insertRaw(Long site, int verseNo, String kind, int variantNo, String status, String bodyText) {
        jdbc.update("INSERT INTO manuscript (site_id, verse_no, kind, variant_no, status, title, body, author_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", site, verseNo, kind, variantNo, status, "직접 넣은 원고", bodyText, ED1);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
