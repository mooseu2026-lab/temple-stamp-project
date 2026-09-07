package com.templestamp.stamp;

import com.templestamp.admin.dto.PendingStampRow;
import com.templestamp.global.type.AccuracyGrade;
import com.templestamp.global.type.StampStatus;
import com.templestamp.global.type.VerifyMethod;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface StampMapper {

    int save(Stamp stamp);

    Optional<Stamp> findById(@Param("stampId") Long stampId);

    /**
     * 그 자리에서 지금 손댈 수 있는 행 하나. 완료 행이 있으면 그것을,
     * 없으면 가장 최근 행을 준다. 호출부가 상태를 보고 진행/거절을 판단한다.
     */
    Optional<Stamp> findLatest(@Param("pilgrimageId") Long pilgrimageId,
                               @Param("courseSiteId") Long courseSiteId);

    int countCompleted(@Param("pilgrimageId") Long pilgrimageId);

    /* ---------------- 상태 전이 ---------------- */

    int markGpsDone(@Param("stampId") Long stampId,
                    @Param("accuracyGrade") AccuracyGrade accuracyGrade);

    int markQrDone(@Param("stampId") Long stampId,
                   @Param("missionId") Long missionId,
                   @Param("sessionMinutes") int sessionMinutes);

    int markCompleted(@Param("stampId") Long stampId,
                      @Param("userSentence") String userSentence,
                      @Param("photoKey") String photoKey,
                      @Param("sessionMinutes") int sessionMinutes);

    /** 심사 대기로 보낸다. 증빙 접수(EVIDENCE) 또는 이동시간 미달(TRAVEL_TIME). */
    int markPending(@Param("stampId") Long stampId,
                    @Param("verifyMethod") VerifyMethod verifyMethod,
                    @Param("evidencePhotoKey") String evidencePhotoKey,
                    @Param("pendingReason") String pendingReason,
                    @Param("userSentence") String userSentence);

    int markReviewed(@Param("stampId") Long stampId,
                     @Param("status") StampStatus status,
                     @Param("reviewedBy") Long reviewedBy,
                     @Param("reviewNote") String reviewNote);

    /** GPS 통과 후 sessionMinutes 를 넘긴 진행 중 도장을 한 번에 EXPIRED 로 넘긴다. */
    int expireStale(@Param("sessionMinutes") int sessionMinutes);

    /* ---------------- 이동시간 검증 ---------------- */

    /** 같은 순례에서 직전에 완료한 도장의 사찰. 없으면 첫 도장이다. */
    Optional<Long> findLastCompletedSiteId(@Param("pilgrimageId") Long pilgrimageId);

    /** 직전 완료 시각으로부터 지금까지 흐른 분. 직전 도장이 없으면 비어 있다. */
    Optional<Integer> findMinutesSinceLastCompleted(@Param("pilgrimageId") Long pilgrimageId);

    /* ---------------- 관리자 ---------------- */

    List<PendingStampRow> findPending(@Param("offset") int offset, @Param("limit") int limit);

    long countPending();

    /* ---------------- 보상 심사(AuditScorer) 근거 ---------------- */

    int countByUserAndMethod(@Param("userId") Long userId,
                             @Param("verifyMethod") VerifyMethod verifyMethod);

    int countCompletedByUser(@Param("userId") Long userId);

    /** 같은 순례에서 이웃한 완료 도장 사이 최소 간격(초). 도장이 1건뿐이면 비어 있다. */
    Optional<Long> findMinCompletionGapSeconds(@Param("pilgrimageId") Long pilgrimageId);

    /** 이동시간 미달로 보류된 이력 수. 이상 활동 신호 중 하나다. */
    int countTravelTimeFlags(@Param("userId") Long userId);
}
