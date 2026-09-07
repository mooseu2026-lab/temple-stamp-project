package com.templestamp.upload;

import com.templestamp.global.config.StorageProperties;
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
    private final StorageProperties storageProperties;

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
     * presign 이 내준 URL 로 올라온 바이트를 받는다(챕터 11-A · provider=local).
     * <p>
     * 이 문이 생기기 전까지 <b>사진 바이트는 한 장도 저장된 적이 없었다.</b> presign 이 가리키던
     * {@code localhost:9000} 에는 아무 서버가 없었고, 전자책 조판이 사진을 못 찾으면
     * "(사진 없음)" 으로 넘어가도록 만들어 둔 것이 그 사실을 덮고 있었다(11-A0 실측).
     * <p>
     * 문을 여는 대신 검사를 넷 건다. 하나라도 빠지면 아무나 아무 이름으로 파일을 올릴 수 있다.
     * <ol>
     *   <li><b>키가 내 것인가</b> — {@code PHOTO/{내 userId}/…}. 남의 폴더에 못 쓴다</li>
     *   <li><b>서명이 맞고 아직 사는가</b> — presign 이 만든 것 말고는 통과하지 못한다</li>
     *   <li><b>형식이 맞는가</b> — jpeg·png 만. 확장자와 Content-Type 이 같아야 한다</li>
     *   <li><b>크기가 상한 안인가</b> — {@code storage.max-upload-bytes}</li>
     * </ol>
     * 순서가 뜻이 있다. 소유자를 먼저 보는 이유는, 남의 키에 대해서는 서명이 맞는지 여부조차
     * 알려 주지 않기 위해서다.
     */
    public void receive(Long userId, String fileKey, long expires, String signature,
                        String contentType, byte[] bytes) {
        UploadPurpose purpose = purposeOf(fileKey);
        if (!ownsKey(userId, purpose, fileKey)) {
            throw new BusinessException(ErrorCode.UPLOAD_4001);
        }
        storageClient.verifySignature("PUT", fileKey, expires, signature);

        String ext = ALLOWED.get(contentType == null ? "" : contentType.split(";")[0].trim());
        if (ext == null || !fileKey.endsWith("." + ext)) {
            // 확장자와 Content-Type 이 어긋나면 거절한다 — .jpg 로 올린 png 는 나중에 조판에서 터진다.
            throw new BusinessException(ErrorCode.UPLOAD_4001);
        }
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException(ErrorCode.UPLOAD_4001);
        }
        if (bytes.length > storageProperties.maxUploadBytes()) {
            throw new BusinessException(ErrorCode.COMMON_4130,
                    "사진은 %.1fMB 까지 올릴 수 있습니다.".formatted(storageProperties.maxUploadBytes() / 1048576.0));
        }
        storageClient.put(fileKey, bytes, contentType);
    }

    /**
     * 주소에서 온 경로의 앞에 붙은 버킷 이름을 벗긴다.
     * <p>
     * presign 은 {@code {endpoint}/{bucket}/{fileKey}} 를 만든다 — S3 와 같은 모양이라
     * 나중에 endpoint 만 S3 주소로 되돌리면 프론트가 보내는 주소의 모양이 그대로다.
     * 그 대신 우리 문은 버킷 토막을 지나서 읽어야 하고, <b>우리 버킷인지도 확인한다</b> —
     * 남의 버킷 이름으로 온 요청을 받아 줄 이유가 없다.
     */
    public String stripBucket(String pathRest) {
        String bucket = storageProperties.bucket();
        if (bucket == null || bucket.isBlank() || pathRest == null) {
            throw new BusinessException(ErrorCode.UPLOAD_4001);
        }
        String head = bucket + "/";
        if (!pathRest.startsWith(head)) {
            throw new BusinessException(ErrorCode.UPLOAD_4001);
        }
        return pathRest.substring(head.length());
    }

    /** 키의 첫 토막이 용도다. 모르는 용도면 우리가 만든 키가 아니다. */
    private static UploadPurpose purposeOf(String fileKey) {
        if (fileKey == null || !fileKey.contains("/")) {
            throw new BusinessException(ErrorCode.UPLOAD_4001);
        }
        try {
            return UploadPurpose.valueOf(fileKey.substring(0, fileKey.indexOf('/')));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.UPLOAD_4001);
        }
    }

    /** 이 키의 파일이 저장소에 실제로 있는가. 키만 받고 올리지 않은 것을 걸러 낸다. */
    public void requireStored(String fileKey) {
        if (fileKey != null && !fileKey.isBlank() && !storageClient.exists(fileKey)) {
            throw new BusinessException(ErrorCode.UPLOAD_4001,
                    "그 사진이 저장소에 없습니다. 업로드를 마친 뒤 다시 시도해 주세요.");
        }
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
