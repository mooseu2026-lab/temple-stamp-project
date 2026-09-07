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
    public static final String ON_THREE_COURSES_COMPLETED = "THREE_COURSES_COMPLETED";
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
    public boolean isStampScoped() {
        return ON_STAMP_COMPLETED.equals(triggerType);
    }
}
