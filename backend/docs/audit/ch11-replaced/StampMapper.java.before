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
                    @Param("siteId") Long siteId,
                    @Param("accuracyGrade") AccuracyGrade accuracyGrade);

    /**
     * QR 통과. 이때 <b>이 세션에서 보여 줄 원고를 못 박는다</b>(챕터 8 §3-1).
     * 이 저장소에서는 도장 행이 곧 인증 세션이라, "세션에 저장" 과 "발행 시 복사" 가 한 칸으로 끝난다.
     * 사용자가 읽는 중에 원고가 퇴역·교체돼도 화면의 글은 바뀌지 않는다(함정 3).
     */
    int markQrDone(@Param("stampId") Long stampId,
                   @Param("missionId") Long missionId,
                   @Param("manuscriptId") Long manuscriptId,
                   @Param("sessionMinutes") int sessionMinutes);

    /** 발행. 확장문구도 여기서 못 박는다 — 전자책(챕터 9)이 그 시점의 글을 실어야 한다(§3-2). */
    int markCompleted(@Param("stampId") Long stampId,
                      @Param("userSentence") String userSentence,
                      @Param("photoKey") String photoKey,
                      @Param("expansionPhraseId") Long expansionPhraseId,
                      @Param("extManuscriptId") Long extManuscriptId,
                      @Param("sessionMinutes") int sessionMinutes);

    /** 심사 승인으로 발행되는 도장의 확장문구를 뒤늦게 못 박는다. 이미 값이 있으면 손대지 않는다(§3-2). */
    int fixExtManuscript(@Param("stampId") Long stampId,
                         @Param("extManuscriptId") Long extManuscriptId);

    /** 심사 대기로 보낸다. 증빙 접수(EVIDENCE) 또는 이동시간 미달(TRAVEL_TIME). */
    int markPending(@Param("stampId") Long stampId,
                    @Param("verifyMethod") VerifyMethod verifyMethod,
                    @Param("evidencePhotoKey") String evidencePhotoKey,
                    @Param("pendingReason") String pendingReason,
                    @Param("userSentence") String userSentence,
                    @Param("expansionPhraseId") Long expansionPhraseId,
                    @Param("siteId") Long siteId);

    int markReviewed(@Param("stampId") Long stampId,
                     @Param("status") StampStatus status,
                     @Param("reviewedBy") Long reviewedBy,
                     @Param("reviewNote") String reviewNote);

    /** 탈퇴 — 그 사람의 도장에 남은 사진 키들. 저장소 큐에 적기 전에 모아 온다. */
    List<String> findPhotoKeysByUser(@Param("userId") Long userId);

    /**
     * 탈퇴 — 도장 <b>행은 남기고 내용만 비운다</b>. 완주·인증서가 이 행 위에 서 있어서 지울 수 없고,
     * 사진과 다짐 문장은 그 사람이 쓴 것이라 남길 수 없다. 그래서 행은 두고 두 칸만 비운다.
     */
    int scrubPersonalByUser(@Param("userId") Long userId);

    /** 탈퇴 — 진행 중이던 인증 세션을 닫는다. 완료된 도장은 완주의 근거라 손대지 않는다. */
    int expireOpenByUser(@Param("userId") Long userId);

    /**
     * 만료 대상의 <b>id 만</b> PK 순으로 한 묶음 가져온다. 갱신은 {@link #expireByIds} 가 PK 로 한다.
     * <p>
     * 조건을 스캔하며 한 번에 지우면 보조 색인(idx_stamp_pending)을 먼저 잠그고 PRIMARY 를 나중에 잠근다.
     * 미션 제출은 반대로 PRIMARY 를 먼저 잠근다 — 두 순서가 엇갈려 교착이 났고, 진 쪽이 사용자 요청이라
     * 500 이 나갔다(최종 점검 F). 그래서 <b>고르기와 갱신을 나눠</b> 갱신이 언제나 PK 부터 잡게 한다.
     * <p>
     * {@code afterId} 로 앞으로만 넘어간다 — 갱신이 0행인 묶음이 나와도 같은 자리를 다시 읽지 않는다.
     */
    List<Long> findStaleIds(@Param("sessionMinutes") int sessionMinutes,
                            @Param("afterId") long afterId,
                            @Param("limit") int limit);

    /**
     * 고른 id 들을 EXPIRED 로 닫는다. 상태 조건을 다시 보므로, 고른 뒤 사용자가 마친 도장은 건드리지 않는다.
     */
    int expireByIds(@Param("ids") List<Long> ids);

    /** 증빙 접수 — 처음부터 PENDING 으로 넣는다. EXPIRED 를 경유하지 않는다. */
    int insertEvidence(Stamp stamp);

    /**
     * 세션이 지났을 때만 한 건을 EXPIRED 로 닫는다. 만료 판정이 SQL 안에 있어서
     * 읽기와 쓰기 사이에 끼어들 틈이 없다. 0행 = 아직 만료가 아니다.
     */
    int markExpiredIfStale(@Param("stampId") Long stampId,
                           @Param("sessionMinutes") int sessionMinutes);

    /** 잠금 읽기. 2·3단계는 이것으로 시작한다(잠금 순서 pilgrimage → stamp). */
    Optional<Stamp> findByIdForUpdate(@Param("stampId") Long stampId);

    /**
     * 하루 5개 한도. 오늘 만든 도장 중 <b>EXPIRED·REJECTED 를 뺀</b> 수다
     * (GPS_DONE·QR_DONE·COMPLETED·PENDING 포함). COMPLETED 만 세면 PENDING 으로 빠져나간 뒤
     * 승인되는 방식으로 한도를 넘길 수 있다.
     */
    int countActiveToday(@Param("userId") Long userId);

    /** 예외 접수 하루 2건 한도. */
    int countEvidenceToday(@Param("userId") Long userId);

    /** Q1 ① — 이 자리에서 그 사람이 이미 받은 완료 도장. 없으면 empty. */
    Optional<Stamp> findCompletedBySlot(@Param("userId") Long userId,
                                        @Param("courseSiteId") Long courseSiteId);

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
