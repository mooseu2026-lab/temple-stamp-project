package com.templestamp.reward;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * reward_policy 테이블 도메인 클래스 — 보상 규칙.
 * 무엇을(rewardType) 언제(triggerType) 주는지만 정한다.
 * 수량 한도 컬럼이 없다 — 한정 수량은 정책 표가 아니라 운영에서 다룰 문제라는 판단이다.
 */
@Getter
@Setter
@NoArgsConstructor
public class RewardPolicy {

    /* reward_type */
    public static final String STAMP = "STAMP";
    public static final String COUPON = "COUPON";
    public static final String PHYSICAL = "PHYSICAL";

    /* trigger_type */
    public static final String ON_STAMP_COMPLETED = "STAMP_COMPLETED";
    public static final String ON_COURSE_COMPLETED = "COURSE_COMPLETED";
    /** 3코스마다 — 3·6·9·12 에서 각각 한 번. 등호가 아니라 배수라 이름을 EVERY 로 바꿨다(챕터 11). */
    public static final String ON_EVERY_THREE_COURSES = "EVERY_THREE_COURSES";
    public static final String ON_ALL_COMPLETED = "ALL_COMPLETED";

    private Long rewardPolicyId;
    private String code;
    private String rewardType;
    private String triggerType;
    private String name;
    private String description;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 도장 단위 보상인가. 근거를 stamp_id 로 남길지 pilgrimage_id 로 남길지 가른다. */
    /**
     * 사용자 단위 보상인가. 특정 도장·특정 코스가 아니라 <b>이 사람의 누적</b>에 대한 보상이다.
     * <p>
     * 이런 보상을 코스 id 로 저장하면 "3번째로 완주한 코스가 무엇이냐" 라는 우연이 키가 된다 —
     * 코스 하나를 반려했다가 재승인하면 다른 코스 id 로 한 행이 더 생겨 실물 기념품이 두 개 나간다(리뷰 2-2).
     */
    public boolean isUserScoped() {
        return ON_EVERY_THREE_COURSES.equals(triggerType) || ON_ALL_COMPLETED.equals(triggerType);
    }

    public boolean isStampScoped() {
        return ON_STAMP_COMPLETED.equals(triggerType);
    }
}
