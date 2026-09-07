package com.templestamp.certificate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * certificate 테이블 도메인 클래스 — 인증서.
 * PILGRIMAGE : 코스 완주. 순례 하나당 한 장(uk_certificate_pilgrimage)
 * HOEHYANG   : 전 사찰 회향. 순례에 매이지 않으므로 pilgrimageId 가 NULL 이다
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Certificate {

    public static final String PILGRIMAGE = "PILGRIMAGE";
    public static final String HOEHYANG = "HOEHYANG";

    private Long certificateId;
    private Long userId;
    private Long pilgrimageId;
    private String certType;
    private String serialNo;
    private LocalDateTime issuedAt;
    private String fileKey;
}
