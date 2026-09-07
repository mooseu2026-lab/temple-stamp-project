package com.templestamp.ebook.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 전자책 인증서 목록의 재료. <b>VALID 만</b> 싣는다 — 회수본은 이 책에 들어가지 않는다. */
@Getter
@Setter
@NoArgsConstructor
public class EbookCertRow {
    private String serialNo;
    private String certType;          // PILGRIMAGE / HOEHYANG
    private String courseName;        // 회향이면 null
    private LocalDateTime issuedAt;
}
