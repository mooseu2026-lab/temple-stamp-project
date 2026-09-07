package com.templestamp.ebook.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 전자책 도장 페이지 한 쪽의 재료(챕터 9 §2-3).
 * <p>
 * {@code extTitle}·{@code extBody} 는 <b>그날 그 사람이 본 문구</b>다 —
 * {@code stamp.ext_manuscript_id} 를 따라가므로 원고가 나중에 퇴역·수정돼도 바뀌지 않는다(챕터 8 §3-2).
 * [사용 위치] EbookMaterialMapper 의 resultType
 */
@Getter
@Setter
@NoArgsConstructor
public class EbookStampRow {
    private Long stampId;
    private Long courseId;
    private String courseName;
    private String regionName;
    private String siteName;
    private LocalDateTime completedAt;
    private String userSentence;      // 없으면 null
    private String photoKey;          // 없으면 null
    private Long extManuscriptId;     // 없으면 null
    private String extTitle;
    private String extBody;
}
