package com.templestamp.global.type;

/**
 * 업로드 용도. presign 발급 시 저장 경로 prefix 를 결정한다.
 */
public enum UploadPurpose {

    PHOTO("photos"),
    EVIDENCE("evidences");

    private final String prefix;

    UploadPurpose(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }
}
