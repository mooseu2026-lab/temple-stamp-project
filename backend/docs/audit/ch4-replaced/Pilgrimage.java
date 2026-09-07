package com.templestamp.pilgrimage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 순례 (회원 × 코스). 코스 카드를 누르는 순간 한 건 생기고, 연타해도 한 건이다.
 * <p>
 * 완주 개수를 컬럼으로 들고 있지 않다. 항상 stamp 에서 COUNT 로 센다. 심사 반려로
 * 완료가 취소될 수 있어, 카운터를 따로 두면 언젠가 실제 도장 수와 어긋난다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Pilgrimage {

    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String COMPLETED = "COMPLETED";

    private Long pilgrimageId;
    private Long userId;
    private Long courseId;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    public boolean isCompleted() {
        return COMPLETED.equals(status);
    }
}
