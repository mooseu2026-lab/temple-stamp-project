// src/main/java/com/templestamp/pilgrimage/Pilgrimage.java
package com.templestamp.pilgrimage;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** pilgrimage 테이블 1행. 완주 수 컬럼이 없다 — 항상 stamp COUNT (스키마 주석). status 는 IN_PROGRESS / COMPLETED 문자열 */
@Getter
@Setter
@NoArgsConstructor
public class Pilgrimage {
    private Long pilgrimageId;
    private Long userId;
    private Long courseId;
    private String status;              // DEFAULT 'IN_PROGRESS'. COMPLETED 전이는 챕터 7 CompletionService 가 한다
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;  // 완주 전 null

    /** 교재 [기본 40] 에는 없다. 챕터 7 CompletionService.afterStampRevoked 가 쓰므로 남긴다. */
    public boolean isCompleted() {
        return "COMPLETED".equals(status);
    }
}
