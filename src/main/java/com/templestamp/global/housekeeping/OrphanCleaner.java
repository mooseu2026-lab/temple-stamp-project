package com.templestamp.global.housekeeping;

import com.templestamp.global.config.EbookProperties;
import com.templestamp.upload.ObjectStorageClient;
import com.templestamp.user.StorageOrphanMapper;
import com.templestamp.user.dto.StorageOrphanRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 파일 삭제 큐 소비(챕터 9 §5 ①).
 * <p>
 * 탈퇴·사진 교체·전자책 정리가 "이 키를 지워 달라" 고 적어 둔 것을 실제로 지운다.
 * 그 자리에서 지우지 않는 이유는 챕터 1 보강에 적힌 그대로다 — 저장소 호출이 실패하면
 * 그 트랜잭션 전체가 롤백되고, "지워 달라" 는 요청이 아무 일도 없던 것이 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanCleaner {

    private final StorageOrphanMapper storageOrphanMapper;
    private final ObjectStorageClient storageClient;
    private final EbookProperties ebookProperties;

    public record Counts(int deleted, int failed) {
    }

    /**
     * 오래된 것부터 한 묶음. <b>저장소에 이미 없는 키는 성공으로 본다</b> —
     * 없는 파일을 못 지웠다고 세면 큐가 영원히 비지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Counts consume() {
        List<StorageOrphanRow> rows = storageOrphanMapper.findPending(ebookProperties.orphanPerRun());
        int deleted = 0;
        int failed = 0;
        for (StorageOrphanRow row : rows) {
            try {
                storageClient.delete(row.getFileKey());   // 이미 없으면 조용히 지나간다
                storageOrphanMapper.markDeleted(row.getStorageOrphanId());
                deleted++;
            } catch (Exception e) {
                storageOrphanMapper.markRetry(row.getStorageOrphanId(),
                        ebookProperties.orphanMaxRetry(), shorten(e.getMessage()));
                failed++;
                log.warn("파일 삭제 실패. key={}, 시도={}", row.getFileKey(), row.getRetryCount() + 1);
            }
        }
        return new Counts(deleted, failed);
    }

    private String shorten(String message) {
        if (message == null) {
            return "알 수 없는 오류";
        }
        return message.length() <= 200 ? message : message.substring(0, 200);
    }
}
