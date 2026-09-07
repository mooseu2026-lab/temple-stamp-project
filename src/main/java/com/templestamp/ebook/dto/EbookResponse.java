package com.templestamp.ebook.dto;

import java.time.LocalDateTime;

/**
 * 전자책 목록 한 건을 내려주는 DTO Record입니다.
 * 저장소 키 대신 downloadable 불리언만 주어 내부 경로가 노출되지 않게 합니다.
 * [사용 위치] EbookService 가 EbookRow → 변환 → Controller 반환
 */
public record EbookResponse(
        Long ebookId,
        String courseName,          // 회향본이면 null
        String ebookType,
        String status,
        boolean downloadable,       // pdfKey 또는 epubKey 존재 여부 — 계산값
        Integer pageCount,          // READY 일 때만
        Long byteSize,              // READY 일 때만
        String failReason,          // FAILED 일 때만
        String downloadUrl,         // READY 일 때만 presigned 10분. 그 밖에는 null(에러가 아니다)
        LocalDateTime createdAt
) {
    /** 목록용 — 링크는 붙이지 않는다. 목록마다 서명을 만들면 쓰지도 않을 URL 을 수십 개 찍는다. */
    public static EbookResponse from(EbookRow row) {
        return of(row, null);
    }

    /** 1권 조회용 — READY 일 때만 링크가 붙는다. */
    public static EbookResponse of(EbookRow row, String downloadUrl) {
        boolean downloadable = row.getPdfKey() != null || row.getEpubKey() != null;
        return new EbookResponse(row.getEbookId(), row.getCourseName(), row.getEbookType(),
                row.getStatus(), downloadable, row.getPageCount(), row.getByteSize(),
                row.getFailReason(), downloadUrl, row.getCreatedAt());
    }
}
