package com.templestamp.admin.dto;

import jakarta.validation.constraints.Size;

/**
 * 생성 완료된 전자책 파일의 저장소 키를 등록하는 요청 DTO Record입니다.
 * (전자책 조판은 외부·배치에서 하고, 결과 키만 여기로 등록한다는 전제)
 * 둘 중 하나는 반드시 있어야 함 — Service 검증. 키 형식 검증도 Service.
 * [사용 위치] AdminEbookController — @RequestBody @Valid
 */
public record AdminEbookFileRequest(

        @Size(max = 500, message = "PDF 키는 500자 이하여야 합니다.")
        String pdfKey,

        @Size(max = 500, message = "EPUB 키는 500자 이하여야 합니다.")
        String epubKey
) {
}
