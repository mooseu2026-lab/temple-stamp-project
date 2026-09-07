package com.templestamp.course.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 코스에 속한 사찰 한 곳의 위치와 인증 기준을 담는 조회 전용 DTO 클래스입니다.
 * course_site · site · site_i18n 조인 결과를 받습니다.
 * [사용 위치] CourseSiteMapper 의 resultType
 */
@Getter
@Setter
@NoArgsConstructor
public class CourseSiteRow {
    private Long courseSiteId;
    private Long courseId;
    private Long siteId;
    private Integer position;        // 코스 안 순번 1~5
    private Integer verseNo;         // course_site.verse_no — 담당 오관게 구절
    private String siteName;         // site_i18n.name(locale) 없으면 site.name
    private String siteStatus;       // ACTIVE 만 인증 대상
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer verifyRadius;    // 인증 반경 m
    private Integer qrVersion;       // QR 서명 검증에 쓰는 현재 버전
    private String qrLocationHint;   // QR 부착 위치 안내
}
