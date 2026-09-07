package com.templestamp.thinkbox.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 생각상자 기록에 코스명·사찰명을 조인해 담는 조회 전용 DTO 클래스입니다.
 * DB 컬럼은 body / is_private 이고 여기 이름은 content / isPrivate 이다 —
 * 매퍼 XML 이 별칭으로 뒤집어 준다(NOT is_private AS is_public).
 * [사용 위치] ThinkboxMapper 의 resultType (thinkbox LEFT JOIN course·site)
 */
@Getter
@Setter
@NoArgsConstructor
public class ThinkboxRow {
    private Long thinkboxId;
    private String content;
    private String source;             // MISSION(자동 기록) / DIRECT(직접 작성)
    private Boolean isPrivate;
    private Boolean isEdited;
    private Long stampId;
    private Long courseId;             // 연결 없으면 null
    private String courseName;
    private Long siteId;
    private String siteName;
    private LocalDateTime createdAt;
}
