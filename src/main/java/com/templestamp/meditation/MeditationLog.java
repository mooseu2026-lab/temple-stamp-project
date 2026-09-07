package com.templestamp.meditation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 재생 기록. 같은 명상을 여러 번 들으면 행이 그만큼 쌓인다. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeditationLog {

    private Long meditationLogId;
    private Long userId;
    private Long meditationId;
    private Integer playedSec;
    private String memo;
    private LocalDateTime createdAt;
}
