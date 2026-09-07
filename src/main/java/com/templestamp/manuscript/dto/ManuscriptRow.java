package com.templestamp.manuscript.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 원고 목록 한 줄(편집자·관리자 공용 조회 결과). 사찰 이름과 작성자 닉네임을 조인해 담는다.
 * [사용 위치] ManuscriptMapper.findRows → ManuscriptResponse
 */
@Getter
@Setter
@NoArgsConstructor
public class ManuscriptRow {
    private Long manuscriptId;
    private Long siteId;            // 기본 원고면 null
    private String siteName;        // 기본 원고면 null
    private Integer verseNo;
    private String kind;
    private Integer variantNo;
    private String status;
    private String title;
    private String body;
    private Long authorId;
    private String authorNickname;
    private Long reviewerId;
    private LocalDateTime reviewedAt;
    private String rejectReason;    // 반려된 적이 있으면 남아 있다(이력)
    private LocalDateTime retiredAt;
    private LocalDateTime createdAt;
}
