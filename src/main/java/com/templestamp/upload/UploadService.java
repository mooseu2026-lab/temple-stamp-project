package com.templestamp.upload;

import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.global.type.UploadPurpose;
import com.templestamp.upload.dto.PresignResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 업로드 URL 발급. DB 를 건드리지 않는다 — 사진 메타는 제출 시점에 PhotoService 가 남긴다.
 * 업로드해 놓고 제출하지 않은 파일은 스토리지 수명 주기 정책으로 정리한다.
 * <p>
 * <b>서버는 바이트를 만지지 않는다.</b> "이 이름으로 잠깐 올려도 된다" 는 서명 URL 만 만들어 주고
 * 앱이 저장소에 직접 올린다. EXIF 제거도 앱 몫이다(명세 5.6) — 서버는 확인할 방법이 없어 요구만 한다.
 */
@Service
@RequiredArgsConstructor
public class UploadService {

    /** 허용 형식. 확장자는 여기서 정해지므로 목록을 넓히면 키 정규식도 함께 넓혀야 한다. */
    private static final Map<String, String> ALLOWED = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png");

    private final ObjectStorageClient storageClient;

    public PresignResponse presign(Long userId, UploadPurpose purpose, String contentType) {
        if (purpose == null) {
            throw new BusinessException(ErrorCode.UPLOAD_4001);
        }
        String ext = ALLOWED.get(contentType);
        if (ext == null) {
            throw new BusinessException(ErrorCode.COMMON_4000,
                    java.util.List.of(new com.templestamp.global.error.ErrorResponse.FieldError(
                            "contentType", "image/jpeg 또는 image/png 만 허용합니다.")));
        }
        String fileKey = storageClient.createFileKey(purpose, userId, ext);
        ObjectStorageClient.Presigned presigned = storageClient.presignPut(fileKey);
        return new PresignResponse(presigned.url(), presigned.fileKey(), presigned.expiresInSeconds());
    }

    /**
     * 제출 쪽 공용 검증 — 이 사용자의, 이 용도의 키인가.
     * 경로를 손으로 지어내거나 남의 키를 그대로 넣는 것을 막는다.
     */
    public static boolean ownsKey(Long userId, UploadPurpose purpose, String key) {
        return key != null
                && key.matches("^" + purpose.name() + "/" + userId + "/[0-9a-f-]{36}\\.(jpg|png)$");
    }
}
