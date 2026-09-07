package com.templestamp.verse;

import com.templestamp.global.type.Tier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 확장문구의 <b>권역(코스) 내 버전 중복 금지</b>(챕터 10 · 기획 명세 §5.4).
 * <p>
 * 한 코스는 자리가 다섯이고 버전도 다섯이다. 규칙이 없으면 1구에서 3번을 읽고 4구에서 또 3번을 읽는 일이
 * 생겨 "다섯 곳을 걸었다" 는 느낌이 옅어진다. 그래서 <b>이 코스에서 이미 읽은 버전 번호</b>는
 * 다른 구절에서도 뺀다.
 * <p>
 * 완화는 세 단계다 — ① 미노출 + 버전 미중복 → ② 미노출 → ③ 가장 오래전에 읽은 것.
 * 다 막아 버리면 코스를 다시 도는 사람의 화면이 비는데, 그것이 더 나쁘다(챕터 5 결정).
 */
@SpringBootTest
class PhraseVersionTest {

    @Autowired PhraseService phraseService;
    @Autowired JdbcTemplate jdbc;

    private long userId;
    private long courseId;
    private List<Integer> verses;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO users (email, password, nickname, role, tier, locale) "
                + "VALUES ('pv-test@test.com', 'x', '버전시험', 'USER', 'AGE30', 'ko')");
        userId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        courseId = jdbc.queryForObject("SELECT course_id FROM course ORDER BY course_id LIMIT 1", Long.class);
        verses = jdbc.queryForList("SELECT verse_no FROM gwan_verse ORDER BY verse_no", Integer.class);

        // 구절마다 다섯 버전을 채운다. 시드가 한 버전만 두고 있으면 규칙을 재 볼 수 없다 —
        // 고를 것이 하나뿐이면 무슨 규칙이든 같은 답이 나온다.
        for (int verseNo : verses) {
            for (int v = 1; v <= 5; v++) {
                jdbc.update("INSERT IGNORE INTO expansion_phrase (verse_no, tier, version_no, text_ko, review_status) "
                                + "VALUES (?, 'AGE30', ?, ?, 'DRAFT')",
                        verseNo, v, "구절 " + verseNo + " 버전 " + v);
            }
        }
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM phrase_seen WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM users WHERE user_id = ?", userId);
    }

    @Test
    @DisplayName("① 한 코스의 다섯 자리에서 버전 번호가 전부 다르다")
    void versionsAreDistinctAcrossOneCourse() {
        List<Integer> picked = new ArrayList<>();

        for (int verseNo : verses) {
            ExpansionPhrase p = phraseService.pickPhrase(userId, courseId, verseNo, Tier.AGE30);
            assertThat(p).isNotNull();
            picked.add(p.getVersionNo());
            // 실제 흐름과 같게, 읽은 것으로 기록하고 다음 자리로 간다.
            phraseService.markPhraseSeen(userId, courseId, p.getExpansionPhraseId());
        }

        assertThat(picked).hasSize(verses.size());
        assertThat(new HashSet<>(picked)).as("다섯 자리에서 버전이 겹치지 않는다").hasSize(verses.size());
    }

    @Test
    @DisplayName("② 버전을 다 쓰면 ②단계로 완화된다 — 200 이 유지되고 화면이 비지 않는다")
    void relaxesToUnseenWhenVersionsExhausted() {
        // 이 코스에서 다섯 버전을 모두 읽은 것으로 만든다. 이제 "버전 미중복" 을 지킬 수 없다.
        for (int verseNo : verses) {
            ExpansionPhrase p = phraseService.pickPhrase(userId, courseId, verseNo, Tier.AGE30);
            phraseService.markPhraseSeen(userId, courseId, p.getExpansionPhraseId());
        }

        // 첫 구절을 다시 열어 본다. 규칙이 막지 않고 무언가를 준다.
        ExpansionPhrase again = phraseService.pickPhrase(userId, courseId, verses.get(0), Tier.AGE30);
        assertThat(again).as("완화 단계가 있으므로 null 이 아니다").isNotNull();
        assertThat(again.getVerseNo()).isEqualTo(verses.get(0));
    }

    @Test
    @DisplayName("③ 그 구절의 문구를 다 읽었으면 ③단계(가장 오래전 것)로 내려간다")
    void relaxesToLeastRecentlySeen() {
        int verseNo = verses.get(0);
        Set<Long> seen = new HashSet<>();

        // 이 구절의 다섯 버전을 전부 읽는다.
        for (int i = 0; i < 5; i++) {
            ExpansionPhrase p = phraseService.pickPhrase(userId, courseId, verseNo, Tier.AGE30);
            assertThat(p).isNotNull();
            seen.add(p.getExpansionPhraseId());
            phraseService.markPhraseSeen(userId, courseId, p.getExpansionPhraseId());
        }

        ExpansionPhrase again = phraseService.pickPhrase(userId, courseId, verseNo, Tier.AGE30);
        assertThat(again).isNotNull();
        assertThat(seen).as("이미 읽은 것 중에서 다시 준다 — 화면을 비우지 않는다")
                .contains(again.getExpansionPhraseId());
    }

    @Test
    @DisplayName("④ 비로그인은 이력이 없으니 규칙을 적용하지 않는다")
    void anonymousIsUnaffected() {
        ExpansionPhrase p = phraseService.pickPhrase(null, null, verses.get(0), Tier.AGE30);
        assertThat(p).isNotNull();
    }
}
