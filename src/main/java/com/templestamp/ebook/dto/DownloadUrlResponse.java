package com.templestamp.ebook.dto;

/**
 * 전자책 다운로드용 임시 URL 과 유효 시간을 내려주는 DTO Record입니다.
 * [사용 위치] EbookService 가 저장소 presign 결과 조립 → Controller 반환
 */
public record DownloadUrlResponse(
        String downloadUrl,
        String format,              // PDF 또는 EPUB — 요청 파라미터를 그대로 반영
        long expiresInSeconds       // PresignResponse 와 단위 통일(초)
) {
}
