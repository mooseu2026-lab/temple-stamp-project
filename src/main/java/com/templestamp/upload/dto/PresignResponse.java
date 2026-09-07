package com.templestamp.upload.dto;

/**
 * 클라이언트가 저장소에 직접 업로드할 수 있는 임시 URL 과 파일 키를 내려주는 DTO Record입니다.
 * 서버는 파일 바이트를 거치지 않으며 DB 도 건드리지 않습니다.
 * fileKey 는 최종 키(PHOTO/{userId}/{uuid}.jpg 또는 EVIDENCE/...) — tmp/ 없음.
 * 클라이언트는 받은 fileKey 를 가공 없이 그대로 제출 요청에 되돌려준다.
 * [사용 위치] UploadService 조립 → UploadController 반환
 */
public record PresignResponse(
        String uploadUrl,           // 저장소 PUT 임시 URL
        String fileKey,             // 제출 시 그대로 돌려줄 최종 키
        long expiresInSeconds       // URL 유효 시간(초)
) {
}
