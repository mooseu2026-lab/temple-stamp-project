package com.templestamp.stamp;

import com.templestamp.admin.dto.PendingStampResponse;
import com.templestamp.admin.dto.StampReviewRequest;
import com.templestamp.course.CourseService;
import com.templestamp.course.dto.CourseSiteRow;
import com.templestamp.course.dto.ProgressResponse;
import com.templestamp.global.config.StampProperties;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.global.response.PageResponse;
import com.templestamp.global.type.StampStatus;
import com.templestamp.global.type.UploadPurpose;
import com.templestamp.global.type.VerifyMethod;
import com.templestamp.pilgrimage.Pilgrimage;
import com.templestamp.pilgrimage.PilgrimageService;
import com.templestamp.photo.PhotoService;
import com.templestamp.site.SiteDistanceMapper;
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
    private final StampProperties stampProperties;

    /* ---------------- 1단계: GPS ---------------- */

    /**
     * 현장 도착 확인. 정확도가 LOW 면 통과시키지 않는다 — 그 등급은 stamp 에 저장조차 되지 않는다.
     */
    @Transactional
    public GpsCheckResponse gpsCheck(Long userId, Long courseSiteId, GpsCheckRequest request) {
        userService.requireLocationAgreement(userId);

        CourseSiteRow slot = courseService.requireSlot(courseSiteId);
        if (!"ACTIVE".equals(slot.getSiteStatus())) {
            throw new BusinessException(ErrorCode.SITE_4090);
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
                slot.getQrLocationHint(),
                expiresAt);
    }

    /* ---------------- 2단계: QR ---------------- */

    /**
     * 사찰 QR 검증. 통과하면 그 사람에게 미션을 배정해 함께 돌려준다.
     */
    @Transactional
    public StampStatusResponse verifyQr(Long userId, Long stampId, QrVerifyRequest request) {
        Stamp stamp = getOwned(userId, stampId);
        CourseSiteRow slot = courseService.requireSlot(stamp.getCourseSiteId());

        if (stamp.getVerifyStatus() != StampStatus.GPS_DONE) {
            throw new BusinessException(ErrorCode.STAMP_4092, "도착 확인을 먼저 마쳐야 합니다.");
        }
        if (stamp.isSessionExpired(sessionMinutes())) {
            throw new BusinessException(ErrorCode.STAMP_4091);
        }

        qrTokenProvider.verify(request.qrToken(), slot.getSiteId(), slot.getQrVersion());

        Mission mission = phraseService.pickMission(
                userId, slot.getCourseId(), slot.getVerseNo(), userService.getTier(userId));

        if (stampMapper.markQrDone(stampId, mission.getMissionId(), sessionMinutes()) == 0) {
            // 검사와 갱신 사이에 60분이 지난 경우. SQL 조건이 최종 방어선이다.
            throw new BusinessException(ErrorCode.STAMP_4091);
        }
        return toStatus(reload(stampId));
    }

    /* ---------------- 3단계: 다짐 ---------------- */

    /**
     * 다짐 제출. 직전 도장에서 이동에 필요한 최소 시간이 지나지 않았으면 완료가 아니라
     * 심사 보류(PENDING·TRAVEL_TIME)로 보낸다 — 물리적으로 불가능한 이동이기 때문이다.
     */
    @Transactional
    public MissionResultResponse submitMission(Long userId, Long stampId, MissionSubmitRequest request) {
        Stamp stamp = getOwned(userId, stampId);
        CourseSiteRow slot = courseService.requireSlot(stamp.getCourseSiteId());

        if (stamp.getVerifyStatus() != StampStatus.QR_DONE) {
            throw new BusinessException(ErrorCode.STAMP_4092, "QR 확인을 먼저 마쳐야 합니다.");
        }
        if (stamp.isSessionExpired(sessionMinutes())) {
            throw new BusinessException(ErrorCode.STAMP_4091);
        }

        String sentence = request.sentence().trim();
        String photoKey = normalizeKey(request.photoKey());

        if (photoKey != null) {
            storageClient.verifyOwnedKey(photoKey, UploadPurpose.PHOTO, userId);
            photoService.register(userId, slot.getSiteId(), photoKey,
                    Boolean.TRUE.equals(request.hasOtherFace()));
        }

        Integer shortfall = travelTimeShortfall(stamp.getPilgrimageId(), slot.getSiteId());
        if (shortfall != null) {
            stampMapper.markPending(stampId, VerifyMethod.GPS_QR, null,
                    Stamp.PENDING_TRAVEL_TIME, sentence);
            log.info("이동시간 미달로 보류. stampId={}, 부족분={}분", stampId, shortfall);

            return new MissionResultResponse(stampId, StampStatus.PENDING.name(),
                    "직전 사찰에서 이동하기에 이른 시간이라 확인이 필요합니다. 관리자 확인 후 반영됩니다.",
                    progressOf(stamp.getPilgrimageId()), List.of(), false, null);
        }

        if (stampMapper.markCompleted(stampId, sentence, photoKey, sessionMinutes()) == 0) {
            throw new BusinessException(ErrorCode.STAMP_4091);
        }

        // 도장의 원본 문장은 못 고치고, 생각상자 사본만 고칠 수 있다.
        thinkboxService.createFromMission(
                userId, stampId, slot.getCourseId(), slot.getSiteId(), sentence);

        CompletionResult completion =
                completionService.afterStampCompleted(userId, stamp.getPilgrimageId(), stampId);

        return new MissionResultResponse(
                stampId, StampStatus.COMPLETED.name(), "도장이 찍혔습니다.",
                progressOf(stamp.getPilgrimageId()), completion.rewards(),
                completion.courseCompleted(), completion.certificateSerial());
    }

    /* ---------------- 대체 경로: 증빙 접수 ---------------- */

    /**
     * GPS·QR 이 불가능한 상황의 예외 접수. 바로 PENDING 이며 관리자 승인을 기다린다.
     */
    @Transactional
    public EvidenceResponse submitEvidence(Long userId, EvidenceRequest request) {
        CourseSiteRow slot = courseService.requireSlot(request.courseSiteId());
        storageClient.verifyOwnedKey(request.photoKey(), UploadPurpose.EVIDENCE, userId);

        Pilgrimage pilgrimage = pilgrimageService.ensureStarted(userId, slot.getCourseId());
        Stamp stamp = findOrCreateForEvidence(pilgrimage.getPilgrimageId(), request.courseSiteId());

        if (stampMapper.markPending(stamp.getStampId(), VerifyMethod.EVIDENCE,
                request.photoKey(), Stamp.PENDING_EVIDENCE, request.sentence().trim()) == 0) {
            throw new BusinessException(ErrorCode.STAMP_4094);
        }

        return new EvidenceResponse(stamp.getStampId(), StampStatus.PENDING.name(),
                "접수되었습니다. 관리자 확인 후 순례 수첩에 반영됩니다.", LocalDateTime.now());
    }

    /* ---------------- 조회 ---------------- */

    public StampStatusResponse getStatus(Long userId, Long stampId) {
        return toStatus(getOwned(userId, stampId));
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
     * 증빙·보류 심사. 승인하면 GPS/QR 을 거치지 않고 바로 COMPLETED 가 되므로
     * 완주 연쇄 처리도 여기서 같이 태운다.
     */
    @Transactional
    public void review(Long adminId, Long stampId, StampReviewRequest request) {
        if (!request.isApprove() && (request.reason() == null || request.reason().isBlank())) {
            throw new BusinessException(ErrorCode.COMMON_4000, "반려 사유를 입력해 주세요.");
        }

        Stamp stamp = reload(stampId);
        if (stamp.getVerifyStatus() != StampStatus.PENDING) {
            throw new BusinessException(ErrorCode.STAMP_4092, "심사 대기 상태가 아닙니다.");
        }

        StampStatus next = request.isApprove() ? StampStatus.COMPLETED : StampStatus.REJECTED;
        if (stampMapper.markReviewed(stampId, next, adminId, request.reason()) == 0) {
            throw new BusinessException(ErrorCode.STAMP_4092);
        }

        Long userId = pilgrimageService.getById(stamp.getPilgrimageId()).getUserId();
        if (request.isApprove()) {
            completionService.afterStampCompleted(userId, stamp.getPilgrimageId(), stampId);
        } else {
            completionService.afterStampRevoked(stamp.getPilgrimageId());
        }
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
            stampMapper.markGpsDone(existing.getStampId(), request.accuracyGrade());
            return reload(existing.getStampId());
        }

        Stamp created = Stamp.builder()
                .pilgrimageId(pilgrimageId)
                .courseSiteId(courseSiteId)
                .verifyStatus(StampStatus.GPS_DONE)
                .verifyMethod(VerifyMethod.GPS_QR)
                .accuracyGrade(request.accuracyGrade())
                .gpsVerifiedAt(LocalDateTime.now())
                .build();
        stampMapper.save(created);
        return created;
    }

    /** 증빙 접수는 GPS 를 거치지 않으므로 행이 없으면 만들어 두고 바로 PENDING 으로 넘긴다. */
    private Stamp findOrCreateForEvidence(Long pilgrimageId, Long courseSiteId) {
        Stamp existing = stampMapper.findLatest(pilgrimageId, courseSiteId).orElse(null);
        if (existing != null) {
            if (existing.isCompleted()) {
                throw new BusinessException(ErrorCode.STAMP_4090);
            }
            if (existing.getVerifyStatus() == StampStatus.PENDING) {
                throw new BusinessException(ErrorCode.STAMP_4093);
            }
            return existing;
        }

        Stamp created = Stamp.builder()
                .pilgrimageId(pilgrimageId)
                .courseSiteId(courseSiteId)
                .verifyStatus(StampStatus.EXPIRED)   // markPending 이 받아 줄 수 있는 시작 상태
                .verifyMethod(VerifyMethod.EVIDENCE)
                .build();
        stampMapper.save(created);
        return created;
    }

    /** 도장에는 user_id 가 없다. 소유는 순례를 통해 확인한다. */
    private Stamp getOwned(Long userId, Long stampId) {
        Stamp stamp = reload(stampId);
        pilgrimageService.getOwned(userId, stamp.getPilgrimageId());
        return stamp;
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
                expiresAt);
    }

    /** 프론트가 "" 를 보내도 사진 없음으로 받는다. 그것 때문에 제출이 실패하면 대가가 더 크다. */
    private String normalizeKey(String key) {
        return key == null || key.isBlank() ? null : key;
    }
}
