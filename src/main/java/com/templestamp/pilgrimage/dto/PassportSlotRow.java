package com.templestamp.pilgrimage.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 여권의 한 칸에 해당하는 사찰과 스탬프 획득 여부를 담는 조회 전용 DTO 클래스입니다.
 * 스탬프가 없으면 관련 필드가 null 입니다.
 * PassportResponse 가 권역 → 코스 → 칸으로 묶어야 하므로 그룹 키(권역·코스·순례)까지 함께 담습니다.
 * [사용 위치] PilgrimageMapper 의 resultType
 */
@Getter
@Setter
@NoArgsConstructor
public class PassportSlotRow {
    // 그룹 키
    private Long regionId;
    private String regionName;
    private Long courseId;
    private String courseName;
    private Long pilgrimageId;          // 미시작이면 null
    private String pilgrimageStatus;    // 미시작이면 null
    // 칸
    private Integer position;
    private Long courseSiteId;
    private String siteName;
    private Long stampId;               // COMPLETED 스탬프 없으면 null
    private LocalDateTime completedAt;
    private String userSentence;        // 미션 다짐 문장
    private String photoKey;
    // 챕터 8 §3-2 — 그 도장에 박힌 확장문구. 도장이 없거나 문구가 없으면 셋 다 null
    private String extTitle;
    private String extBody;
    private Integer extVariantNo;
}
