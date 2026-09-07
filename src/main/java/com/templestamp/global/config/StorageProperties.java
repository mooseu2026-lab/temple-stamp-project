package com.templestamp.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * storage.* — 오브젝트 스토리지.
 * 서버는 파일 바이트를 통과시키지 않고 presigned URL 만 발급한다.
 */
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(
        String provider,
        String bucket,
        String endpoint,
        String secretKey,
        long presignSeconds,
        long maxUploadBytes,
        /**
         * provider=local 일 때 파일 바이트를 실제로 두는 자리.
         * 지금까지 이 저장소는 <b>서명만 하고 바이트를 저장하지 않았다</b> — 사진도 키만 기록됐다.
         * 챕터 9 는 PDF 를 만들어 두어야 하고 청소기가 지울 것도 있어야 해서 실물이 필요해졌다.
         * 운영(S3)에서는 이 값이 쓰이지 않는다.
         */
        String localDir
) {
    public String localDirOrDefault() {
        return localDir == null || localDir.isBlank()
                ? System.getProperty("java.io.tmpdir") + "/temple-stamp-storage"
                : localDir;
    }
}
