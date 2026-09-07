package com.templestamp.upload;

import com.templestamp.global.config.StorageProperties;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.global.type.UploadPurpose;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 오브젝트 스토리지 접근. 서버는 파일 바이트를 통과시키지 않고 presigned URL 만 발급한다.
 * 앱이 스토리지로 직접 올리므로 대용량 사진이 WAS 메모리를 지나가지 않는다.
 * <p>
 * 키 규칙: {@code {PURPOSE}/{userId}/{uuid}.jpg} — tmp/ 접두어 없이 처음부터 최종 키다.
 * 임시 키를 만들었다가 옮기는 방식은 "옮기기 전에 앱이 죽으면 고아 파일이 남는다" 는 문제를
 * 항상 달고 다닌다. 키에 userId 가 박혀 있어 제출 때 소유자 검증이 문자열 비교로 끝난다.
 * <p>
 * 서명은 HMAC-SHA256 기반의 자체 규격이다. 실제 S3 로 붙일 때는 이 클래스만 SigV4 구현으로
 * 갈아 끼우면 되고, 호출부(UploadService·PhotoService·EbookService)는 손대지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ObjectStorageClient {

    private final StorageProperties storageProperties;

    /** presign 결과 한 벌. */
    public record Presigned(String fileKey, String url, long expiresInSeconds) {
    }

    /**
     * 최종 업로드 키를 만든다. 예) PHOTO/17/9f3c1b2a-....-ab12.jpg
     */
    public String createFileKey(UploadPurpose purpose, Long userId) {
        return createFileKey(purpose, userId, "jpg");
    }

    /**
     * 키 규칙 {@code {PURPOSE}/{userId}/{uuid}.{ext}}. 이름 앞에 사용자 번호를 박아 두면
     * 나중에 제출된 키가 그 사람 것인지 정규식 한 줄로 확인된다({@link #verifyOwnedKey}).
     */
    public String createFileKey(UploadPurpose purpose, Long userId, String ext) {
        return "%s/%d/%s.%s".formatted(purpose.name(), userId, UUID.randomUUID(), ext);
    }

    public Presigned presignPut(String fileKey) {
        long validity = storageProperties.presignSeconds();
        long expiresAt = Instant.now().getEpochSecond() + validity;
        String url = buildUrl("PUT", fileKey, expiresAt);
        return new Presigned(fileKey, url, validity);
    }

    /** 다운로드용 서명 URL. 전자책·증빙 사진처럼 권한 확인 후에만 내주는 파일에 쓴다. */
    public String presignGet(String fileKey, long validitySeconds) {
        long expiresAt = Instant.now().getEpochSecond() + validitySeconds;
        return buildUrl("GET", fileKey, expiresAt);
    }

    public String presignGet(String fileKey) {
        return presignGet(fileKey, storageProperties.presignSeconds());
    }

    public long getPresignSeconds() {
        return storageProperties.presignSeconds();
    }

    /**
     * 클라이언트가 돌려준 키가 우리가 발급한 모양인지, 그리고 그 사용자 것인지 확인한다.
     * 키를 손으로 바꿔 남의 파일을 스탬프에 붙이는 시도를 여기서 막는다.
     * (요청 DTO 의 @Pattern 이 형식을 보고, 여기서 소유자를 본다)
     */
    public void verifyOwnedKey(String fileKey, UploadPurpose purpose, Long userId) {
        if (fileKey == null || fileKey.isBlank() || fileKey.contains("..")) {
            throw new BusinessException(ErrorCode.UPLOAD_4001);
        }
        String[] parts = fileKey.split("/");
        if (parts.length != 3
                || !parts[0].equals(purpose.name())
                || !parts[1].equals(String.valueOf(userId))) {
            throw new BusinessException(ErrorCode.UPLOAD_4001);
        }
    }

    /**
     * presign 이 만든 서명이 맞는지, 아직 살아 있는지 본다(챕터 11-A).
     * <p>
     * 지금까지 {@link #sign} 은 만들기만 하고 <b>검사한 적이 없었다</b> — 검사 주체가
     * 운영의 S3 라는 전제였기 때문이다. provider=local 에는 그 서버가 없어서 사진 바이트가
     * 한 장도 저장되지 않았고, 그래서 우리 앱이 수신 문을 열게 됐다. 문을 열면 검사도 우리 몫이다.
     * <p>
     * 만료를 먼저 본다 — 서명이 맞아도 시간이 지났으면 못 쓴다. 그리고 비교는
     * {@link MessageDigest#isEqual} 로 한다. {@code equals} 는 첫 다른 글자에서 바로 끝나
     * 걸린 시간으로 앞부분이 맞았는지가 새어 나간다(타이밍 공격).
     *
     * @throws BusinessException 서명이 다르거나 만료면 {@code UPLOAD-4001}
     */
    public void verifySignature(String method, String fileKey, long expiresAt, String signature) {
        if (signature == null || signature.isBlank()) {
            throw new BusinessException(ErrorCode.UPLOAD_4001);
        }
        if (Instant.now().getEpochSecond() > expiresAt) {
            throw new BusinessException(ErrorCode.UPLOAD_4001);
        }
        byte[] want = sign(method, fileKey, expiresAt).getBytes(StandardCharsets.UTF_8);
        byte[] got = signature.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(want, got)) {
            throw new BusinessException(ErrorCode.UPLOAD_4001);
        }
    }

    private String buildUrl(String method, String fileKey, long expiresAt) {
        StorageProperties storage = storageProperties;
        return "%s/%s/%s?expires=%d&signature=%s".formatted(
                trimSlash(storage.endpoint()),
                storage.bucket(),
                fileKey,
                expiresAt,
                URLEncoder.encode(sign(method, fileKey, expiresAt), StandardCharsets.UTF_8));
    }

    private String sign(String method, String fileKey, long expiresAt) {
        String canonical = String.join("\n",
                method, storageProperties.bucket(), fileKey, String.valueOf(expiresAt));
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    storageProperties.secretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("presign 서명 생성 실패", e);
        }
    }

    /* ---------------- 파일 실물 (provider=local) ----------------
       서명 URL 만으로는 파일이 존재하지 않는다. 챕터 9 가 PDF 를 만들어 두어야 하고
       청소기가 지울 것도 있어야 해서, local 일 때는 디렉터리에 실제로 쓴다.
       S3 로 갈아 끼울 때 이 네 메서드만 SDK 호출로 바꾸면 되고 호출부는 그대로다. */

    public void put(String fileKey, byte[] bytes, String contentType) {
        Path target = resolve(fileKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("저장소에 쓰지 못했다: " + fileKey, e);
        }
    }

    public byte[] getBytes(String fileKey) {
        try {
            return Files.readAllBytes(resolve(fileKey));
        } catch (IOException e) {
            throw new UncheckedIOException("저장소에서 읽지 못했다: " + fileKey, e);
        }
    }

    public boolean exists(String fileKey) {
        return Files.isRegularFile(resolve(fileKey));
    }

    /**
     * 지운다. <b>이미 없으면 성공으로 본다</b> — 청소기가 같은 키를 두 번 집거나,
     * 사람이 먼저 지운 경우까지 실패로 세면 큐가 영원히 비지 않는다(챕터 9 §5 ①).
     */
    public void delete(String fileKey) {
        try {
            Files.deleteIfExists(resolve(fileKey));
        } catch (IOException e) {
            throw new UncheckedIOException("저장소에서 지우지 못했다: " + fileKey, e);
        }
    }

    /**
     * 키를 경로로 바꾼다. 키에 {@code ..} 가 섞이면 뿌리 밖으로 나갈 수 있으므로
     * 정규화한 뒤 뿌리 안에 있는지 확인한다 — 키는 사용자 입력에서 올 수 있다.
     */
    private Path resolve(String fileKey) {
        if (fileKey == null || fileKey.isBlank() || fileKey.contains("..")) {
            throw new BusinessException(ErrorCode.UPLOAD_4001);
        }
        Path root = Path.of(storageProperties.localDirOrDefault()).toAbsolutePath().normalize();
        Path target = root.resolve(fileKey).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException(ErrorCode.UPLOAD_4001);
        }
        return target;
    }

    private String trimSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
