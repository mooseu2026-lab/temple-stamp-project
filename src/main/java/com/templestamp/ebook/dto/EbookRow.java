package com.templestamp.ebook.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 전자책에 코스명을 조인해 담는 조회 전용 DTO 클래스입니다.
 * 저장소 키(pdfKey·epubKey)를 포함하지만 이 값은 응답까지 나가지 않습니다.
 * [사용 위치] EbookMapper 의 resultType (ebook LEFT JOIN pilgrimage·course)
 */
@Getter
@Setter
@NoArgsConstructor
public class EbookRow {
    private Long ebookId;
    private Long userId;
    private Long pilgrimageId;
    private Long courseId;         // 회향본·중간본이면 null
    private String courseName;
    private String ebookType;      // PILGRIMAGE(코스본) / INTERIM(전자일기장) / HOEHYANG(회향본) / PERSONAL(개인 소장본)
    private Integer milestone;     // 전자일기장의 3·6·9·12. 그 밖의 종류는 null
    private String status;         // REQUESTED / READY / FAILED
    private String failReason;     // FAILED 일 때만
    private Integer pageCount;     // READY 일 때만
    private Long byteSize;         // READY 일 때만
    private String pdfKey;         // 내부 저장소 키 — 응답 금지
    private String epubKey;        // 내부 저장소 키 — 응답 금지
    private LocalDateTime createdAt;
}
