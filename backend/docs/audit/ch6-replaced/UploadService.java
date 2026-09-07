package com.templestamp.upload;

import com.templestamp.global.type.UploadPurpose;
import com.templestamp.upload.dto.PresignResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 업로드 URL 발급. DB 를 건드리지 않는다 — 사진 메타는 제출 시점에 PhotoService 가 남긴다.
 * 업로드해 놓고 제출하지 않은 파일은 스토리지 수명 주기 정책으로 정리한다.
 */
@Service
@RequiredArgsConstructor
public class UploadService {

    private final ObjectStorageClient storageClient;

    public PresignResponse presign(Long userId, UploadPurpose purpose) {
        String fileKey = storageClient.createFileKey(purpose, userId);
        ObjectStorageClient.Presigned presigned = storageClient.presignPut(fileKey);
        return new PresignResponse(presigned.url(), presigned.fileKey(), presigned.expiresInSeconds());
    }
}
