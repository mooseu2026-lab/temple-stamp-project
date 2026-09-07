package com.templestamp.admin.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 관리자 확인 대기(PENDING) 스탬프에 신청자·사찰 정보를 조인해 담는 조회 전용 DTO 클래스입니다.
 * 이동시간 미달 건과 EVIDENCE 접수 건이 함께 조회됩니다.
 * [사용 위치] StampMapper 의 resultType (stamp JOIN pilgrimage·users·course·course_site·site)
 */
@Getter
@Setter
@NoArgsConstructor
public class PendingStampRow {
    private Long stampId;
    private Long userId;
    private String nickname;
    private String courseName;
    private String siteName;
    private String verifyMethod;       // GPS_QR(이동시간 미달) / EVIDENCE(예외 접수)
    private String pendingReason;      // TRAVEL_TIME / EVIDENCE
    private String userSentence;       // 다짐 또는 상황 설명 문장
    private String photoKey;           // 내부 키 — 응답 전 임시 URL 로 변환
    private LocalDateTime createdAt;
}
