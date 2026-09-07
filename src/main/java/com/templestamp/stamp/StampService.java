package com.templestamp.stamp;

import com.templestamp.admin.dto.PendingStampResponse;
import com.templestamp.admin.dto.StampReviewRequest;
import com.templestamp.course.CourseService;
import com.templestamp.course.SlotSiteMapper;
import com.templestamp.course.dto.CourseSiteRow;
import com.templestamp.course.dto.ProgressResponse;
import com.templestamp.global.config.StampProperties;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.global.response.PageResponse;
import com.templestamp.global.type.StampStatus;
import com.templestamp.global.type.UploadPurpose;
import com.templestamp.global.type.VerifyMethod;
import com.templestamp.manuscript.Manuscript;
import com.templestamp.manuscript.ManuscriptSelector;
import com.templestamp.manuscript.dto.ExtPhraseResponse;
import com.templestamp.manuscript.dto.ManuscriptTextResponse;
import com.templestamp.pilgrimage.Pilgrimage;
import com.templestamp.pilgrimage.PilgrimageService;
import com.templestamp.photo.PhotoService;
import com.templestamp.site.Site;
import com.templestamp.site.SiteDistanceMapper;
import com.templestamp.site.SiteMapper;
import com.templestamp.stamp.dto.CompletionResult;
import com.templestamp.stamp.dto.EvidenceRequest;
import com.templestamp.stamp.dto.EvidenceResponse;
import com.templestamp.stamp.dto.GpsCheckRequest;
import com.templestamp.stamp.dto.GpsCheckResponse;
import com.templestamp.stamp.dto.MissionResultResponse;
import com.templestamp.stamp.dto.MissionSubmitRequest;
import com.templestamp.stamp.dto.QrVerifyRequest;
import com.templestamp.stamp.dto.StampStatusResponse;
import com.templestamp.thinkbox.ThinkboxService;
import com.templestamp.upload.ObjectStorageClient;
import com.templestamp.user.UserService;
import com.templestamp.verse.Mission;
import com.templestamp.verse.PhraseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 스탬프 인증 진행. GPS 확인 → QR 검증 → 다짐 제출 3단계를 상태 기계로 다룬다.
 * <p>
 * 각 단계는 앞 단계의 상태를 반드시 확인한다. QR 만 따로 호출해 현장에 가지 않고 도장을 받는 걸
 * 막기 위해서다. GPS 통과 시각부터 60분 안에 끝내야 하고, 넘기면 EXPIRED 로 닫힌다.
 * <p>
 * 서버는 사용자 좌표를 받지도 저장하지도 않는다. 앱이 사찰 좌표(코스 상세로 미리 받은 값)와의
 * 거리를 계산해 "반경 안인가" 와 "정확도 등급" 만 보낸다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StampService {

    private final StampMapper stampMapper;
    private final CourseService courseService;
    private final PilgrimageService pilgrimageService;
    private final PhraseService phraseService;
    private final QrTokenProvider qrTokenProvider;
    private final PhotoService photoService;
    private final ThinkboxService thinkboxService;
    private final ObjectStorageClient storageClient;
    private final SiteDistanceMapper siteDistanceMapper;
    private final UserService userService;
    private final CompletionService completionService;
    private final StampExpireService stampExpireService;   // REQUIRES_NEW — 만료를 예외보다 먼저 남긴다
    private final SlotSiteMapper slotSiteMapper;           // v4 — 이 자리의 후보인가
    private final SiteMapper siteMapper;                   // 409 메시지·응답의 사찰 이름
    private final StampProperties stampProperties;
    private final ApplicationEventPublisher eventPublisher;   // 심사 뒤 재집계를 커밋 밖으로 보낸다
    private final ManuscriptSelector manuscriptSelector;      // 챕터 8 — 이 사람에게 보여 줄 원고

    /* ---------------- 1단계: GPS ---------------- */

    /**
     * 현장 도착 확인. 정확도가 LOW 면 통과시키지 않는다 — 그 등급은 stamp 에 저장조차 되지 않는다.
     */
    @Transactional
    public GpsCheckResponse gpsCheck(Long userId, Long courseSiteId, GpsCheckRequest request) {
        userService.requireLocationAgreement(userId);

        CourseSiteRow slot = courseService.requireSlot(courseSiteId);

        // v4 — 한 자리에 후보가 여럿이다. 대표(slot.getSiteId())가 아니라 사용자가 보낸 사찰이 기준이고,
        // 그 사찰이 이 자리의 후보인지부터 본다. 아니면 남의 자리 도장을 이 자리에 찍을 수 있다.
        if (!slotSiteMapper.exists(courseSiteId, request.siteId())) {
            throw new BusinessException(ErrorCode.COURSE_4001, "이 자리의 후보 사찰이 아닙니다.");
        }
        Site site = siteMapper.findActiveById(request.siteId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SITE_4090));

        // 하루 한도를 자리 검사보다 먼저 본다(교재 순서). 한도에 걸리면 어느 자리를 눌러도 안 되므로
        // "이 자리는 이미 끝났다" 보다 "오늘은 여기까지다" 가 사용자에게 쓸모 있는 답이다.
        if (stampMapper.countActiveToday(userId) >= stampProperties.dailyLimit()) {
            throw new BusinessException(ErrorCode.STAMP_4291);
        }

        // 이미 이 자리를 끝냈으면 어디에서 언제 받았는지까지 알려 준다(Q1 ①).
        // 프론트는 이 409 를 받고 곧바로 GET /api/stamps/by-slot/{courseSiteId} 로 그 도장을 보여준다.
        Stamp done = stampMapper.findCompletedBySlot(userId, courseSiteId).orElse(null);
        if (done != null) {
            throw new BusinessException(ErrorCode.STAMP_4090,
                    "이미 발행된 스탬프입니다 — " + siteNameOf(done.getSiteId()) + " · " + issuedDateOf(done));
        }

        if (!request.accuracyGrade().isAcceptable()) {
            throw new BusinessException(ErrorCode.STAMP_4001);
        }
        if (!Boolean.TRUE.equals(request.withinRadius())) {
            throw new BusinessException(ErrorCode.STAMP_4000);
        }

        Pilgrimage pilgrimage = pilgrimageService.ensureStarted(userId, slot.getCourseId());
        Stamp stamp = findOrCreate(pilgrimage.getPilgrimageId(), courseSiteId, request);

        LocalDateTime expiresAt = LocalDateTime.now()
                .plusMinutes(stampProperties.sessionMinutes());

        return new GpsCheckResponse(
                stamp.getStampId(),
                StampStatus.GPS_DONE.name(),
                site.getQrLocationHint(),   // v4 — 대표가 아니라 실제로 서 있는 그 절의 안내문
                expiresAt);
    }

    /* ---------------- 2단계: QR ---------------- */

    /**
     * 사찰 QR 검증. 통과하면 그 사람에게 미션을 배정해 함께 돌려준다.
     */
    @Transactional
    public StampStatusResponse verifyQr(Long userId, Long stampId, QrVerifyRequest request) {
        expireOrThrow(stampId);                            // ★ 잠그기 전에. 이유는 메서드 주석에 있다
        Stamp stamp = lockOwned(userId, stampId);
        CourseSiteRow slot = courseService.requireSlot(stamp.getCourseSiteId());

        if (stamp.getVerifyStatus() != StampStatus.GPS_DONE) {
            throw new BusinessException(ErrorCode.STAMP_4092, "도착 확인을 먼저 마쳐야 합니다.");
        }

        // v4 — 1단계에서 서 있던 그 사찰의 QR 이어야 한다. 대표 사찰 기준으로 보면
        // 후보 A 에서 도착 확인하고 대표 B 의 QR 로 통과하는 길이 열린다.
        Site site = siteMapper.findActiveById(stamp.getSiteId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SITE_4090));
        qrTokenProvider.verify(request.qrToken(), site.getSiteId(), site.getQrVersion());

        // 사찰을 함께 넘긴다 — 과제는 이제 사찰 풀에서도 나온다(챕터 10 · 가중 50/30/20).
        Mission mission = phraseService.pickMission(
                userId, slot.getCourseId(), stamp.getSiteId(), slot.getVerseNo(), userService.getTier(userId));

        // 챕터 8 §3-1 — 여기서 원고를 고르고 도장 행에 못 박는다. 이 저장소에서는 도장 행이 곧
        // 인증 세션이라, 세션이 살아 있는 동안 원고가 퇴역·교체돼도 읽던 글이 바뀌지 않는다.
        Manuscript page = manuscriptSelector.pickMission(userId, stamp.getSiteId(), slot.getVerseNo());

        if (stampMapper.markQrDone(stampId, mission.getMissionId(),
                page.getManuscriptId(), sessionMinutes()) == 0) {
            // 검사와 갱신 사이에 60분이 지난 아주 좁은 창. SQL 조건이 최종 방어선이다.
            // 여기서는 EXPIRED 를 남기지 않는다 — 이 트랜잭션이 이 행을 이미 잠그고 있어서
            // REQUIRES_NEW 로 같은 행을 UPDATE 하면 자기 자신과 교착한다(아래 expireIfStale 주석).
            // 이 행은 청소기(StampExpireService.expireStale)가 5분 안에 닫는다.
            throw new BusinessException(ErrorCode.STAMP_4091);
        }
        return toStatus(reload(stampId));
    }

    /* ---------------- 3단계: 다짐 ---------------- */

    /**
     * 다짐 제출. 직전 도장에서 이동에 필요한 최소 시간이 지나지 않았으면 완료가 아니라
     * 심사 보류(PENDING·TRAVEL_TIME)로 보낸다 — 물리적으로 불가능한 이동이기 때문이다.
     * <p>
     * <b>잠금 순서: user → stamp/slot → completion → certificate/reward</b> (챕터 7 보강 B-1).
     * 이 경로는 응답에 인증서 번호와 보상을 실어 보내야 해서 완주 연쇄를 커밋 뒤로 미룰 수 없다
     * (관리자 심사 경로만 AFTER_COMMIT 으로 뺐다). 대신 순서를 고정해 원형 대기를 막는다.
     */
    @Transactional
    public MissionResultResponse submitMission(Long userId, Long stampId, MissionSubmitRequest request) {
        expireOrThrow(stampId);                            // ★ 잠그기 전에
        userService.lockForCompletion(userId);             // ★ 잠금 순서 첫 칸 — 아래 완주 연쇄까지 이 순서를 지킨다
        Stamp stamp = lockOwned(userId, stampId);
        CourseSiteRow slot = courseService.requireSlot(stamp.getCourseSiteId());

        if (stamp.getVerifyStatus() != StampStatus.QR_DONE) {
            throw new BusinessException(ErrorCode.STAMP_4092, "QR 확인을 먼저 마쳐야 합니다.");
        }

        // 클라이언트가 본 확장문구가 이 구절·이 계층의 것인지 서버가 확인한다. 아니면 null — 기록만 건너뛴다.
        Long phraseId = phraseService.validatePhraseFor(
                request.expansionPhraseId(), slot.getVerseNo(), userService.getTier(userId));

        String sentence = request.sentence().trim();
        String photoKey = normalizeKey(request.photoKey());

        if (photoKey != null) {
            storageClient.verifyOwnedKey(photoKey, UploadPurpose.PHOTO, userId);
            photoService.register(userId, stamp.getSiteId(), photoKey,
                    Boolean.TRUE.equals(request.hasOtherFace()));
        }

        // v4 — 이동시간도 대표가 아니라 실제로 인증한 사찰 사이의 거리로 잰다
        Integer shortfall = travelTimeShortfall(stamp.getPilgrimageId(), stamp.getSiteId());
        if (shortfall != null) {
            stampMapper.markPending(stampId, VerifyMethod.GPS_QR, null,
                    Stamp.PENDING_TRAVEL_TIME, sentence, phraseId, null);   // site_id 는 1단계에서 이미 박혔다
            log.info("이동시간 미달로 보류. stampId={}, 부족분={}분", stampId, shortfall);

            return new MissionResultResponse(stampId, StampStatus.PENDING.name(),
                    "직전 사찰에서 이동하기에 이른 시간이라 확인이 필요합니다. 관리자 확인 후 반영됩니다.",
                    progressOf(stamp.getPilgrimageId()), List.of(), false, null,
                    null);   // 아직 발행이 아니다 — 문구는 승인 시점에 박힌다(§3-2)
        }

        // §3-2 — 발행 시점의 문구를 고정한다. 뒤에 원고가 바뀌어도 이 도장의 글은 그대로다.
        Manuscript ext = manuscriptSelector.pickExt(userId, stamp.getSiteId(), slot.getVerseNo());

        if (stampMapper.markCompleted(stampId, sentence, photoKey, phraseId,
                ext == null ? null : ext.getManuscriptId(), sessionMinutes()) == 0) {
            throw new BusinessException(ErrorCode.STAMP_4091);   // 위와 같은 이유로 청소기에 맡긴다
        }

        // 완료 뒤에만 남기는 기록들. 미리보기(싱글페이지)에서는 절대 남기지 않는다 — 챕터 2 결정.
        if (phraseId != null) {
            phraseService.markPhraseSeen(userId, slot.getCourseId(), phraseId);
        }

        // 도장의 원본 문장은 못 고치고, 생각상자 사본만 고칠 수 있다.
        thinkboxService.createFromMission(
                userId, stampId, slot.getCourseId(), stamp.getSiteId(), sentence);

        CompletionResult completion =
                completionService.afterStampCompleted(userId, stamp.getPilgrimageId(), stampId);

        return new MissionResultResponse(
                stampId, StampStatus.COMPLETED.name(), "도장이 찍혔습니다.",
                progressOf(stamp.getPilgrimageId()), completion.rewards(),
                completion.courseCompleted(), completion.certificateSerial(),
                ExtPhraseResponse.from(ext));
    }

    /* ---------------- 대체 경로: 증빙 접수 ---------------- */

    /**
     * GPS·QR 이 불가능한 상황의 예외 접수. 바로 PENDING 이며 관리자 승인을 기다린다.
     */
    @Transactional
    public EvidenceResponse submitEvidence(Long userId, EvidenceRequest request) {
        CourseSiteRow slot = courseService.requireSlot(request.courseSiteId());
        // v4 — 증빙도 어느 절에서 낸 것인지 남긴다. 승인되면 이 값으로 이동시간·여권 사찰명이 정해진다.
        if (!slotSiteMapper.exists(request.courseSiteId(), request.siteId())) {
            throw new BusinessException(ErrorCode.COURSE_4001, "이 자리의 후보 사찰이 아닙니다.");
        }
        if (stampMapper.countEvidenceToday(userId) >= stampProperties.evidenceDailyLimit()) {
            throw new BusinessException(ErrorCode.STAMP_4292);
        }
        storageClient.verifyOwnedKey(request.photoKey(), UploadPurpose.EVIDENCE, userId);

        Pilgrimage pilgrimage = pilgrimageService.ensureStarted(userId, slot.getCourseId());
        Stamp stamp = insertOrReuseEvidence(pilgrimage.getPilgrimageId(), request);

        return new EvidenceResponse(stamp.getStampId(), StampStatus.PENDING.name(),
                "접수되었습니다. 관리자 확인 후 순례 수첩에 반영됩니다.", LocalDateTime.now());
    }

    /* ---------------- 조회 ---------------- */

    public StampStatusResponse getStatus(Long userId, Long stampId) {
        return toStatus(getOwned(userId, stampId));
    }

    /** Q1 ① — 이 자리에서 내가 받은 완료 도장. 없으면 404. */
    public StampStatusResponse findCompletedBySlot(Long userId, Long courseSiteId) {
        return toStatus(stampMapper.findCompletedBySlot(userId, courseSiteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STAMP_4040)));
    }

    /* ---------------- 관리자 심사 ---------------- */

    public PageResponse<PendingStampResponse> getPending(int page, int size) {
        long total = stampMapper.countPending();
        List<PendingStampResponse> items = stampMapper.findPending(page * size, size).stream()
                .map(row -> PendingStampResponse.of(row,
                        row.getPhotoKey() == null ? null : storageClient.presignGet(row.getPhotoKey())))
                .toList();
        return PageResponse.of(items, page, size, total);
    }

    /**
     * 증빙·보류 심사, 그리고 이미 찍힌 도장의 <b>회수·재승인</b>.
     * <pre>
     *   PENDING   → COMPLETED (승인)   · REJECTED (반려)
     *   COMPLETED → REJECTED  (회수 — 부정 인증이 뒤늦게 드러난 경우)
     *   REJECTED  → COMPLETED (재승인 — 소명이 받아들여진 경우)
     * </pre>
     * 회수·재승인이 없으면 완주 취소 연쇄(챕터 7 §2-4)를 부를 길이 아예 없다 —
     * 대기 중인 도장은 애초에 완주로 세지 않으므로 그것을 반려해도 완주가 깨지지 않는다.
     * 그 밖의 상태(진행 중·만료)는 심사 대상이 아니다.
     */
    @Transactional
    public void review(Long adminId, Long stampId, StampReviewRequest request) {
        if (!request.isApprove() && (request.reason() == null || request.reason().isBlank())) {
            throw new BusinessException(ErrorCode.COMMON_4000, "반려 사유를 입력해 주세요.");
        }

        Stamp stamp = reload(stampId);
        StampStatus current = stamp.getVerifyStatus();
        boolean allowed = request.isApprove()
                ? current == StampStatus.PENDING || current == StampStatus.REJECTED
                : current == StampStatus.PENDING || current == StampStatus.COMPLETED;
        if (!allowed) {
            throw new BusinessException(ErrorCode.STAMP_4092, "심사할 수 있는 상태가 아닙니다.");
        }

        StampStatus next = request.isApprove() ? StampStatus.COMPLETED : StampStatus.REJECTED;
        try {
            if (stampMapper.markReviewed(stampId, next, adminId, request.reason()) == 0) {
                throw new BusinessException(ErrorCode.STAMP_4092);
            }
        } catch (DuplicateKeyException e) {
            // 그 사이 같은 자리에 다른 도장이 완료됐다. 한 자리에 완료 도장은 하나뿐이다(uk_stamp_completed).
            throw new BusinessException(ErrorCode.STAMP_4090, "그 자리에는 이미 완료된 도장이 있습니다.");
        }

        Long userId = pilgrimageService.getById(stamp.getPilgrimageId()).getUserId();

        // 심사 승인도 발행이다(§3-2). GPS·QR 을 거치지 않은 증빙 도장은 여기서 처음 문구를 받는다.
        if (request.isApprove() && stamp.getExtManuscriptId() == null) {
            Manuscript ext = manuscriptSelector.pickExt(userId, stamp.getSiteId(),
                    courseService.requireSlot(stamp.getCourseSiteId()).getVerseNo());
            if (ext != null) {
                stampMapper.fixExtManuscript(stampId, ext.getManuscriptId());
            }
        }
        // 완주 재집계는 이 트랜잭션이 커밋된 뒤에 돈다(CompletionEventListener·챕터 7 §2-3).
        // 도장 행을 잠근 채 완주·인증서·보상까지 잡지 않기 위해서다.
        eventPublisher.publishEvent(new StampReviewedEvent(
                userId, stamp.getPilgrimageId(), stampId, request.isApprove()));
        log.info("증빙 심사 {}. stampId={}, adminId={}",
                request.isApprove() ? "승인" : "반려", stampId, adminId);
    }

    /* ---------------- 내부 ---------------- */

    private int sessionMinutes() {
        return stampProperties.sessionMinutes();
    }

    /**
     * 직전 완료 도장에서 지금까지 흐른 시간이 site_distance 의 최소 이동시간에 못 미치면
     * 부족한 분을 돌려준다. 첫 도장이거나 등록되지 않은 쌍이면 null(검사 안 함).
     */
    private Integer travelTimeShortfall(Long pilgrimageId, Long siteId) {
        Long previousSiteId = stampMapper.findLastCompletedSiteId(pilgrimageId).orElse(null);
        if (previousSiteId == null) {
            return null;
        }

        int required = siteDistanceMapper.findMinMinutes(previousSiteId, siteId)
                .orElse(stampProperties.defaultTravelMinutes());
        if (required <= 0) {
            return null;
        }

        Integer elapsed = stampMapper.findMinutesSinceLastCompleted(pilgrimageId).orElse(null);
        if (elapsed == null || elapsed >= required) {
            return null;
        }
        return required - elapsed;
    }

    private ProgressResponse progressOf(Long pilgrimageId) {
        Pilgrimage pilgrimage = pilgrimageService.getById(pilgrimageId);
        return ProgressResponse.of(pilgrimageId,
                stampMapper.countCompleted(pilgrimageId), pilgrimage.getStatus());
    }

    /** 만료·반려로 닫혔던 행이 있으면 되살리고, 없으면 새로 만든다. */
    private Stamp findOrCreate(Long pilgrimageId, Long courseSiteId, GpsCheckRequest request) {
        Stamp existing = stampMapper.findLatest(pilgrimageId, courseSiteId).orElse(null);

        if (existing != null) {
            if (existing.isCompleted()) {
                throw new BusinessException(ErrorCode.STAMP_4090);
            }
            if (existing.getVerifyStatus() == StampStatus.PENDING) {
                throw new BusinessException(ErrorCode.STAMP_4093);
            }
            stampMapper.markGpsDone(existing.getStampId(), request.siteId(), request.accuracyGrade());
            return reload(existing.getStampId());
        }

        Stamp created = Stamp.builder()
                .pilgrimageId(pilgrimageId)
                .courseSiteId(courseSiteId)
                .siteId(request.siteId())
                .verifyStatus(StampStatus.GPS_DONE)
                .verifyMethod(VerifyMethod.GPS_QR)
                .accuracyGrade(request.accuracyGrade())
                .gpsVerifiedAt(LocalDateTime.now())
                .build();
        stampMapper.save(created);
        return created;
    }

    /**
     * 증빙 접수. 그 자리에 진행 중이던 행이 있으면 그것을 PENDING 으로 넘기고, 없으면
     * <b>처음부터 PENDING 으로</b> 새로 넣는다(교재 [기본 45] {@code insertEvidence}).
     */
    private Stamp insertOrReuseEvidence(Long pilgrimageId, EvidenceRequest request) {
        Stamp existing = stampMapper.findLatest(pilgrimageId, request.courseSiteId()).orElse(null);
        if (existing != null) {
            if (existing.isCompleted()) {
                throw new BusinessException(ErrorCode.STAMP_4090);
            }
            if (existing.getVerifyStatus() == StampStatus.PENDING) {
                throw new BusinessException(ErrorCode.STAMP_4093);
            }
            if (stampMapper.markPending(existing.getStampId(), VerifyMethod.EVIDENCE,
                    request.photoKey(), Stamp.PENDING_EVIDENCE, request.sentence().trim(), null,
                    request.siteId()) == 0) {
                throw new BusinessException(ErrorCode.STAMP_4094);
            }
            return existing;
        }

        Stamp created = Stamp.builder()
                .pilgrimageId(pilgrimageId)
                .courseSiteId(request.courseSiteId())
                .siteId(request.siteId())
                .userSentence(request.sentence().trim())
                .evidencePhotoKey(request.photoKey())
                .build();
        stampMapper.insertEvidence(created);
        return created;
    }

    /** 도장에는 user_id 가 없다. 소유는 순례를 통해 확인한다. */
    private Stamp getOwned(Long userId, Long stampId) {
        Stamp stamp = reload(stampId);
        requireOwner(userId, stamp);
        return stamp;
    }

    /**
     * 쓰기 경로 전용 — 순례를 먼저 잠그고 그다음 도장을 잠근다.
     * <b>이 순서를 바꾸면 관리자 심사(같은 순서)와 교착한다.</b> 잠근 뒤에 다시 읽는 이유는
     * REPEATABLE READ 에서 잠그지 않은 재조회가 옛 스냅샷을 보기 때문이다(정리.md §6-4).
     */
    private Stamp lockOwned(Long userId, Long stampId) {
        Stamp peek = reload(stampId);
        requireOwner(userId, peek);
        pilgrimageService.getOwned(userId, peek.getPilgrimageId());
        return stampMapper.findByIdForUpdate(stampId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STAMP_4040));
    }

    private void requireOwner(Long userId, Stamp stamp) {
        if (!pilgrimageService.getById(stamp.getPilgrimageId()).getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.STAMP_4031);
        }
    }

    /**
     * 세션이 지났으면 EXPIRED 를 <b>먼저 남기고</b> 409 를 던진다. 순서가 반대이면 예외가 트랜잭션을 롤백해
     * "만료라고 응답했는데 DB 는 아직 진행 중" 인 상태가 남는다(정리.md §6-2).
     * <p>
     * <b>반드시 행을 잠그기 전에 불러야 한다.</b> {@code findByIdForUpdate} 로 잠근 뒤 {@code REQUIRES_NEW} 로
     * 같은 행을 UPDATE 하면 바깥 트랜잭션이 쥔 잠금을 안쪽 트랜잭션이 기다리다 <b>자기 자신과 교착</b>한다
     * (MySQL 은 50초 뒤 Lock wait timeout → 500). 정리.md §6-7 · 교재 ch5.md 정정(2026-09-06).
     * <p>
     * 만료 판정은 SQL 의 WHERE 안에 있다 — 읽고 나서 쓰는 사이에 끼어들 틈이 없다.
     * 이 검사를 통과한 뒤 잠금 안에서 다시 만료되는 좁은 창은 UPDATE 의
     * {@code gps_verified_at >= …} 조건이 막고, 그 행은 청소기가 닫는다.
     */
    private void expireOrThrow(Long stampId) {
        if (stampExpireService.expireIfStale(stampId, sessionMinutes())) {
            throw new BusinessException(ErrorCode.STAMP_4091);
        }
    }

    private String siteNameOf(Long siteId) {
        if (siteId == null) {
            return "사찰 미상";
        }
        return siteMapper.findById(siteId).map(Site::getName).orElse("사찰 미상");
    }

    private String issuedDateOf(Stamp stamp) {
        return stamp.getMissionVerifiedAt() == null
                ? "발행일 미상"
                : stamp.getMissionVerifiedAt().toLocalDate().toString();
    }

    private Stamp reload(Long stampId) {
        return stampMapper.findById(stampId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STAMP_4040));
    }

    private StampStatusResponse toStatus(Stamp stamp) {
        LocalDateTime expiresAt = stamp.getGpsVerifiedAt() == null || !stamp.getVerifyStatus().isInProgress()
                ? null
                : stamp.getGpsVerifiedAt().plusMinutes(sessionMinutes());

        return new StampStatusResponse(
                stamp.getStampId(),
                stamp.getCourseSiteId(),
                stamp.getVerifyStatus().name(),
                stamp.getGpsVerifiedAt(),
                stamp.getQrVerifiedAt(),
                stamp.getMissionVerifiedAt(),
                expiresAt,
                stamp.getSiteId(),
                stamp.getSiteId() == null ? null : siteNameOf(stamp.getSiteId()),
                stamp.getPhotoKey(),
                stamp.getUserSentence(),
                ManuscriptTextResponse.from(manuscriptSelector.findById(stamp.getManuscriptId())),
                ExtPhraseResponse.from(manuscriptSelector.findById(stamp.getExtManuscriptId())));
    }

    /** 프론트가 "" 를 보내도 사진 없음으로 받는다. 그것 때문에 제출이 실패하면 대가가 더 크다. */
    private String normalizeKey(String key) {
        return key == null || key.isBlank() ? null : key;
    }
}
