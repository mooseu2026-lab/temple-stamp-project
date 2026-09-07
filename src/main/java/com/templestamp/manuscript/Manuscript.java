package com.templestamp.manuscript;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * manuscript 표 도메인 — 사찰 × 오관게 구에 붙는 글 한 편.
 * <pre>
 *   DRAFT ──제출──▶ SUBMITTED ──승인──▶ APPROVED ──퇴역──▶ RETIRED
 *     ▲                  │
 *     └──수정────────  REJECTED (사유 필수)
 * </pre>
 * <b>지우는 길이 없다.</b> 퇴역만 있다 — 옛 도장이 그 원고를 참조하고 있어서,
 * 지우면 이미 발행된 도장의 글이 사라진다(챕터 8 함정 4).
 * <p>
 * {@code siteId} 가 NULL 이면 <b>기본 원고</b>다. 사찰 원고가 없을 때 쓰이고, 구·종류마다 한 편뿐이다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Manuscript {

    public static final String MISSION = "MISSION";
    public static final String EXT = "EXT";

    public static final String DRAFT = "DRAFT";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String RETIRED = "RETIRED";

    private Long manuscriptId;
    private Long siteId;          // NULL 이면 기본 원고
    private Long siteKey;         // 생성 컬럼 IFNULL(site_id, 0) — 유니크가 이것으로 걸린다
    private Integer verseNo;
    private String kind;
    private Integer variantNo;
    private String status;
    private String title;
    private String body;
    private Long authorId;
    private Long reviewerId;
    private LocalDateTime reviewedAt;
    private String rejectReason;
    private LocalDateTime retiredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isDefault() {
        return siteId == null;
    }
}
