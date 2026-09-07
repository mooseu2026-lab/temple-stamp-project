package com.templestamp.certificate.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 인증서에 발급자 닉네임과 코스명을 조인해 담는 조회 전용 DTO 클래스입니다.
 * [사용 위치] CertificateMapper 의 resultType (certificate JOIN users, LEFT JOIN pilgrimage·course)
 */
@Getter
@Setter
@NoArgsConstructor
public class CertificateRow {
    private Long certificateId;
    private String serialNo;       // 예: PG-2026-000012
    private String certType;       // PILGRIMAGE(코스 완주) / HOEHYANG(전 사찰 회향)
    private String status;         // VALID / REVOKED — 회수된 것도 조회된다(§3-4)
    private String courseName;     // 회향본이면 null
    private String nickname;       // users.nickname 조인 — 공개 응답에서 마스킹 대상
    private LocalDateTime issuedAt;
    private LocalDateTime revokedAt;   // 유효하면 null
}
