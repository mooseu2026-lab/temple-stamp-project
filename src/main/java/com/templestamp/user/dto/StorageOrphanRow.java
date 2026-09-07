package com.templestamp.user.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 청소기가 집어 가는 파일 키 한 줄(챕터 9 §5 ①). */
@Getter
@Setter
@NoArgsConstructor
public class StorageOrphanRow {
    private Long storageOrphanId;
    private String fileKey;
    private String reason;
    private Integer retryCount;
}
