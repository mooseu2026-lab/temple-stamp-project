package com.templestamp.ebook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ebook 테이블 도메인 클래스 — 전자책 생성 큐.
 * <p>
 * 카탈로그가 아니라 대기열이다. 완주 시점에 QUEUED 로 한 건 넣어 두면 외부 조판 워커가
 * BUILDING → READY 로 옮긴다. 조판 자체는 시간이 걸리는 작업이라 요청 스레드에서 하지 않는다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ebook {

    /* ebook_type */
    public static final String PILGRIMAGE = "PILGRIMAGE";   // 코스 완주본
    public static final String INTERIM = "INTERIM";         // 3코스 중간본
    public static final String HOEHYANG = "HOEHYANG";       // 전 사찰 회향본
    public static final String PERSONAL = "PERSONAL";       // 개인 소장본(챕터 9). 완주와 무관하다

    /* status */
    /**
     * 옛 이름은 QUEUED·BUILDING 이었다. 챕터 9 에서 REQUESTED 하나로 줄였다 —
     * 있는 행을 FOR UPDATE 로 잠그고 그 트랜잭션 안에서 만들면 중간 상태가 필요 없고,
     * 중간 상태는 "BUILDING 인 채 죽은 행" 을 회수하는 코드를 또 부른다.
     */
    public static final String REQUESTED = "REQUESTED";
    public static final String READY = "READY";
    public static final String FAILED = "FAILED";

    private Long ebookId;
    private Long userId;
    private Long pilgrimageId;
    private String ebookType;
    private String snapshotHash;   // 재료 정렬 → SHA-256. 같으면 같은 책(챕터 9 §2-1)
    private String status;
    private String pdfKey;
    private String epubKey;
    private String failReason;
    private Integer pageCount;     // READY 일 때만
    private Long byteSize;         // READY 일 때만
    private Integer retryCount;
    private LocalDateTime buildingStartedAt;
    private LocalDateTime queuedAt;
    private LocalDateTime builtAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isReady() { return READY.equals(status); }

    public boolean isRequested() { return REQUESTED.equals(status); }

    public boolean isHoehyang() { return HOEHYANG.equals(ebookType); }
}
