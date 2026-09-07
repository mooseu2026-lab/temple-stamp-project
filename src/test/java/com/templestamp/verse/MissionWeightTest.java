package com.templestamp.verse;

import com.templestamp.global.type.Tier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 행동과제 50/30/20 가중(챕터 10 · 기획 명세 §5.4).
 * <p>
 * 축이 없던 시절에는 어느 사찰에서 열어도 같은 과제가 나왔다. 세 풀로 나눈 뒤 <b>실제 비율이
 * 규칙대로 나오는지</b>는 한 번 뽑아 봐서는 알 수 없어 표본을 크게 잡는다.
 * <p>
 * 5,000회는 임의로 고른 수가 아니다. 확률 0.5 의 이항분포에서 표준편차가 약 0.7%p 라,
 * ±5%p 를 벗어나면 우연이 아니라 규칙이 틀린 것이다.
 */
@SpringBootTest
class MissionWeightTest {

    private static final int DRAWS = 5_000;
    private static final double TOLERANCE = 0.05;   // ±5%p

    @Autowired PhraseService phraseService;
    @Autowired JdbcTemplate jdbc;

    private long userId;
    private long courseId;
    private long siteId;
    private int verseNo;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO users (email, password, nickname, role, tier, locale) "
                + "VALUES ('mw-test@test.com', 'x', '가중시험', 'USER', 'AGE30', 'ko')");
        userId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        courseId = jdbc.queryForObject("SELECT course_id FROM course ORDER BY course_id LIMIT 1", Long.class);
        siteId = jdbc.queryForObject("SELECT site_id FROM site ORDER BY site_id LIMIT 1", Long.class);
        verseNo = jdbc.queryForObject("SELECT verse_no FROM gwan_verse ORDER BY verse_no LIMIT 1", Integer.class);

        // 세 풀에 넉넉히 넣는다. 표본을 5,000 번 뽑는 동안 어느 풀도 마르면 안 된다 —
        // 마르는 순간 그 풀의 몫이 다른 풀로 넘어가 비율이 규칙과 달라진다.
        seed(MissionScope.SITE, 20);
        seed(MissionScope.VERSE, 20);
        seed(MissionScope.COMMON, 20);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM task_seen WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM mission WHERE origin_ref = 'MW-TEST'");
        jdbc.update("DELETE FROM users WHERE user_id = ?", userId);
    }

    private void seed(MissionScope scope, int count) {
        for (int i = 1; i <= count; i++) {
            jdbc.update("INSERT INTO mission (verse_no, tier, scope, site_id, variant_no, body, origin_ref, review_status) "
                            + "VALUES (?, 'AGE30', ?, ?, ?, ?, 'MW-TEST', 'DRAFT')",
                    scope == MissionScope.VERSE ? verseNo : null,
                    scope.name(),
                    scope == MissionScope.SITE ? siteId : null,
                    100 + i,   // variant_no 는 TINYINT 다 — 127 을 넘기면 잘린다
                    scope.name() + " 과제 " + i);
        }
    }

    @Test
    @DisplayName("① 세 풀이 다 차 있으면 50 / 30 / 20 으로 갈린다 — 5,000회 ±5%p")
    void weightsAreFiftyThirtyTwenty() {
        Map<MissionScope, Integer> hits = new EnumMap<>(MissionScope.class);
        for (MissionScope s : MissionScope.values()) {
            hits.put(s, 0);
        }

        // peek 은 노출 이력을 남기지 않는다. 그래서 5,000번을 뽑아도 풀이 마르지 않는다 —
        // pick 으로 돌리면 60편을 다 소진한 뒤부터 대체안만 나와 비율이 무너진다.
        for (int i = 0; i < DRAWS; i++) {
            Mission m = phraseService.peekMission(userId, courseId, siteId, verseNo, Tier.AGE30);
            hits.merge(m.getScope(), 1, Integer::sum);
        }

        double site = hits.get(MissionScope.SITE) / (double) DRAWS;
        double verse = hits.get(MissionScope.VERSE) / (double) DRAWS;
        double common = hits.get(MissionScope.COMMON) / (double) DRAWS;

        assertThat(site).isCloseTo(0.50, org.assertj.core.data.Offset.offset(TOLERANCE));
        assertThat(verse).isCloseTo(0.30, org.assertj.core.data.Offset.offset(TOLERANCE));
        assertThat(common).isCloseTo(0.20, org.assertj.core.data.Offset.offset(TOLERANCE));
    }

    @Test
    @DisplayName("② 한 풀이 비면 남은 풀끼리 다시 나눈다 — 전체 100 을 기준으로 뽑으면 아무것도 못 고르는 창이 생긴다")
    void emptyPoolIsRedistributed() {
        jdbc.update("DELETE FROM mission WHERE origin_ref = 'MW-TEST' AND scope = 'SITE'");

        int verse = 0;
        int draws = 1_000;
        for (int i = 0; i < draws; i++) {
            Mission m = phraseService.peekMission(userId, courseId, siteId, verseNo, Tier.AGE30);
            assertThat(m.getScope()).isNotEqualTo(MissionScope.SITE);
            if (m.getScope() == MissionScope.VERSE) {
                verse++;
            }
        }
        // VERSE 30 · COMMON 20 이 남았으니 60% · 40% 가 된다.
        assertThat(verse / (double) draws).isCloseTo(0.60, org.assertj.core.data.Offset.offset(TOLERANCE));
    }

    @Test
    @DisplayName("③ SITE·VERSE 를 비우면 COMMON 만 나온다")
    void onlyCommonRemains() {
        jdbc.update("DELETE FROM mission WHERE origin_ref = 'MW-TEST' AND scope IN ('SITE','VERSE')");
        // 시드의 VERSE 과제까지 이 구절에서 안 보이게 막는다 — 이미 본 것으로 표시한다.
        jdbc.update("INSERT IGNORE INTO task_seen (user_id, course_id, mission_id) "
                + "SELECT ?, ?, mission_id FROM mission WHERE scope = 'VERSE' AND verse_no = ? AND tier = 'AGE30'",
                userId, courseId, verseNo);

        for (int i = 0; i < 30; i++) {
            assertThat(phraseService.peekMission(userId, courseId, siteId, verseNo, Tier.AGE30).getScope())
                    .isEqualTo(MissionScope.COMMON);
        }
    }

    @Test
    @DisplayName("④ 세 풀이 다 비면 대체안을 준다 — 미션 없이 도장을 끝낼 수는 없다")
    void fallsBackWhenAllPoolsEmpty() {
        jdbc.update("INSERT IGNORE INTO task_seen (user_id, course_id, mission_id) "
                + "SELECT ?, ?, mission_id FROM mission WHERE tier = 'AGE30'", userId, courseId);

        Mission m = phraseService.peekMission(userId, courseId, siteId, verseNo, Tier.AGE30);
        assertThat(m).isNotNull();
        assertThat(m.getMissionId()).isNotNull();
    }

    @Test
    @DisplayName("⑤ 배정(pick)은 노출 이력을 남기고, 미리보기(peek)는 남기지 않는다")
    void onlyPickRecordsSeen() {
        int before = seenCount();
        phraseService.peekMission(userId, courseId, siteId, verseNo, Tier.AGE30);
        assertThat(seenCount()).isEqualTo(before);

        phraseService.pickMission(userId, courseId, siteId, verseNo, Tier.AGE30);
        assertThat(seenCount()).isEqualTo(before + 1);
    }

    private int seenCount() {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM task_seen WHERE user_id = ? AND course_id = ?",
                Integer.class, userId, courseId);
        return n == null ? 0 : n;
    }
}
