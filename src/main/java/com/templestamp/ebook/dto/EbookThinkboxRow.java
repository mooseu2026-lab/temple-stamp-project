package com.templestamp.ebook.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 전자책 생각상자 페이지의 재료.
 * <b>비공개 글도 들어간다</b> — 개인 소장본이라 남에게 나가지 않는다(챕터 9 §2-3).
 */
@Getter
@Setter
@NoArgsConstructor
public class EbookThinkboxRow {
    private Long thinkboxId;
    private String siteName;          // 사찰에 매이지 않은 자유 기록이면 null
    private String content;
    private boolean isPrivate;
    private boolean edited;
    private LocalDateTime createdAt;
}
