package com.templestamp.verse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 미션 노출 이력. 같은 코스 안에서 같은 미션이 다시 나오지 않게 한다. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskSeen {

    private Long taskSeenId;
    private Long userId;
    private Long courseId;
    private Long missionId;
    private LocalDateTime seenAt;
}
