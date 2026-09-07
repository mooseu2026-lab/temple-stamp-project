package com.templestamp.stamp;

import com.templestamp.global.type.AccuracyGrade;
import com.templestamp.global.type.StampStatus;
import com.templestamp.global.type.VerifyMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 스탬프 — 코스 한 자리의 인증 결과. ★ 이 프로젝트의 핵심 테이블.
 *
 * <p><b>위도·경도 컬럼이 없다.</b> 앱이 사찰 좌표와의 거리를 계산해 통과 여부와 정확도 등급만
 * 보내므로, 서버는 사용자가 어디에 있었는지 저장하지 않는다.
 *
 * <p><b>중복은 DB가 막는다.</b> completedCourseSiteId 는 COMPLETED 일 때만 값이 생기는
 * 생성 컬럼이고 (pilgrimage_id, completed_course_site_id) 에 UNIQUE 가 걸려 있다.
 * 그래서 완료 도장은 자리당 딱 하나만 존재하고, 만료·반려된 행은 같은 자리에 여러 개 남아도 된다.
 *
 * <p><b>세션 만료 컬럼이 없다.</b> gpsVerifiedAt 부터 60분이 지나면 만료로 본다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Stamp {

    private Long stampId;
    private Long pilgrimageId;
    private Long courseSiteId;
    private StampStatus verifyStatus;
    private VerifyMethod verifyMethod;
    private AccuracyGrade accuracyGrade;
    private LocalDateTime gpsVerifiedAt;
    private LocalDateTime qrVerifiedAt;
    private LocalDateTime missionVerifiedAt;
    private Long missionId;
    private Long manuscriptId;        // 이 세션에서 보여 준 사찰 원고(챕터 8)
    private Long extManuscriptId;     // 발행 시 못 박은 확장문구(챕터 8)
    /** 그때 사용자가 읽은 확장문구. 기록은 챕터 5 의 완료 처리에서 넣는다. */
    private Long expansionPhraseId;
    /**
     * v4 — 실제로 인증한 후보 사찰. 한 자리(course_site)에 후보가 여럿이라 대표만으로는
     * 어디에서 찍었는지 알 수 없다. QR 검증·이동시간·생각상자·여권 표시가 전부 이 값을 쓴다.
     */
    private Long siteId;
    private String userSentence;
    private String photoKey;
    private String evidencePhotoKey;
    private String pendingReason;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewNote;
    private Long completedCourseSiteId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** pending_reason 값. 증빙 사진 심사 / 이동시간 미달. */
    public static final String PENDING_EVIDENCE = "EVIDENCE";
    public static final String PENDING_TRAVEL_TIME = "TRAVEL_TIME";

    public boolean isCompleted() {
        return verifyStatus == StampStatus.COMPLETED;
    }

    /** GPS 통과 시각으로부터 sessionMinutes 가 지났는가. */
    public boolean isSessionExpired(int sessionMinutes) {
        return gpsVerifiedAt != null
                && gpsVerifiedAt.plusMinutes(sessionMinutes).isBefore(LocalDateTime.now());
    }
}
