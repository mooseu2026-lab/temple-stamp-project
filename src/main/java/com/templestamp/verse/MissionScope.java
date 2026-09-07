package com.templestamp.verse;

/**
 * 행동과제의 출처 축(챕터 10 · 기획 명세 §5.4).
 * <p>
 * 세 풀에서 <b>50 / 30 / 20</b> 가중으로 하나를 고른다. 축이 없던 시절에는 어느 사찰에서 열어도
 * 같은 과제가 나왔고, "사찰마다 다른 과제" 라는 명세의 핵심이 빠져 있었다.
 * <p>
 * 가중은 <b>비어 있지 않은 풀들 사이에서만</b> 나눈다 — SITE 과제가 아직 없는 사찰에서
 * 20%의 확률로 아무것도 못 고르는 일이 없어야 한다.
 */
public enum MissionScope {

    /** 이 사찰의 과제. {@code site_id} 가 채워져 있고 구절은 없다. */
    SITE(50),

    /** 이 구절의 과제. 챕터 5 부터 있던 기존 36편이 여기다. */
    VERSE(30),

    /** 구절도 사찰도 가리지 않는 과제. */
    COMMON(20);

    private final int weight;

    MissionScope(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
