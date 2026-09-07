package com.templestamp.stamp;

import com.templestamp.certificate.Certificate;
import com.templestamp.certificate.CertificateService;
import com.templestamp.course.CourseService;
import com.templestamp.course.dto.ProgressResponse;
import com.templestamp.ebook.Ebook;
import com.templestamp.ebook.EbookService;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.config.RewardProperties;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.pilgrimage.Pilgrimage;
import com.templestamp.pilgrimage.PilgrimageMapper;
import com.templestamp.pilgrimage.PilgrimageService;
import com.templestamp.reward.RewardPolicy;
import com.templestamp.reward.RewardService;
import com.templestamp.reward.dto.RewardResponse;
import com.templestamp.stamp.dto.CompletionResult;
import com.templestamp.stamp.dto.RecountResponse;
import com.templestamp.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 도장 하나가 확정된 뒤의 연쇄 처리를 한곳에 모은다.
 * <pre>
 *   도장 확정
 *     → 5칸 다 찼으면 코스 완주: 완주 행 · 인증서 · 코스본 대기열
 *       → 완주 코스가 3개면 중간본 대기열
 *       → 모든 코스를 마쳤으면 회향 인증서 · 회향본 대기열
 *     → 보상 적립(맨 마지막)
 * </pre>
 * StampService 가 이 흐름을 직접 들고 있으면 GPS/QR/다짐 검증 사이에 완주 처리가 섞여
 * 나중에 어느 쪽을 고쳐도 다른 쪽이 깨진다.
 * <p>
 * <b>완주 판정은 여기서 따로 세지 않는다.</b> 진행률과 완주가 각자 세면 "진행률 100%인데 완주 아님"
 * 같은 모순이 생긴다 — 둘 다 {@link PilgrimageService#progressOf} 한 곳에서 나온다(챕터 7 §2-1·함정 4).
 * <p>
 * <b>잠금 순서는 user → stamp/slot → completion → certificate/reward</b> 다(챕터 7 보강 B-1).
 * 이 클래스의 모든 진입점이 user 부터 잡고, 안에서도 완주 행 → 인증서 → 보상 순으로 쓴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompletionService {

    /** 중간 편집본을 만드는 완주 코스 수. */
    private static final int INTERIM_COURSE_COUNT = 3;

    private final PilgrimageMapper pilgrimageMapper;
    private final PilgrimageService pilgrimageService;
    private final UserService userService;
    private final CourseService courseService;
    private final CertificateService certificateService;
    private final EbookService ebookService;
    private final RewardService rewardService;
    private final RewardProperties rewardProperties;

    /**
     * 다짐 제출·관리자 승인 뒤의 연쇄. 다짐 제출 경로는 응답에 인증서 번호와 보상을 실어야 해서
     * 이 호출이 도장 트랜잭션 안에 있고, 관리자 승인 경로는 커밋 뒤 새 트랜잭션에서 부른다.
     */
    @Transactional
    public CompletionResult afterStampCompleted(Long userId, Long pilgrimageId, Long stampId) {
        userService.lockForCompletion(userId);      // ① user

        // ② completion — 판정은 진행률과 같은 계산에서 나온다
        if (!courseCompleted(userId, pilgrimageId)) {
            return CompletionResult.notCompleted(
                    rewardService.grantOrRestore(userId, RewardPolicy.ON_STAMP_COMPLETED, stampId, null));
        }

        // ③ certificate
        Certificate certificate = completeCourse(userId, pilgrimageId);

        // ④ reward — 마지막. 도장 보상까지 여기서 한 번에 정리한다
        List<RewardResponse> rewards = new ArrayList<>(
                rewardService.grantOrRestore(userId, RewardPolicy.ON_STAMP_COMPLETED, stampId, null));
        rewards.addAll(completionRewards(userId, pilgrimageId));

        return new CompletionResult(true,
                certificate.getCertificateId(), certificate.getSerialNo(), rewards);
    }

    /**
     * 심사 반려 등으로 완료 도장이 줄었을 때 되돌린다(챕터 7 §2-4).
     * 몇 번을 불러도 결과가 같다 — 이미 되돌아가 있으면 아무 일도 하지 않는다.
     */
    @Transactional
    public void afterStampRevoked(Long pilgrimageId) {
        Pilgrimage pilgrimage = pilgrimageMapper.findById(pilgrimageId);
        if (pilgrimage == null) {
            throw new BusinessException(ErrorCode.PILGRIMAGE_4040);
        }
        userService.lockForCompletion(pilgrimage.getUserId());      // ① user — 되돌릴 때도 같은 순서다
        if (courseCompleted(pilgrimage.getUserId(), pilgrimageId) || !pilgrimage.isCompleted()) {
            return;
        }
        cancelCourse(pilgrimage.getUserId(), pilgrimageId);
    }

    /**
     * 한 사람의 모든 코스를 다시 세어 있어야 할 모양으로 맞춘다(챕터 7 §2-2 네 분기).
     * <pre>
     *   5/5 인데 완주 행이 없다   → 세운다(created)
     *   5/5 이고 완주 행이 있다   → 아무것도 안 한다(noop)
     *   5/5 가 아닌데 완주 행이 있다 → 취소한다(canceled)
     *   5/5 도 아니고 행도 없다   → 아무것도 안 한다(noop)
     * </pre>
     * 도장 승인·반려가 어떤 이유로 연쇄를 못 태웠을 때 손으로 맞추는 길이다.
     * 몇 번을 돌려도 같은 결과가 나온다.
     */
    @Transactional
    public RecountResponse recount(Long userId) {
        userService.lockForCompletion(userId);
        int created = 0, canceled = 0, noop = 0;

        for (Pilgrimage pilgrimage : pilgrimageMapper.findByUserId(userId)) {
            boolean done = courseCompleted(userId, pilgrimage.getPilgrimageId());
            if (done && !pilgrimage.isCompleted()) {
                completeCourse(userId, pilgrimage.getPilgrimageId());
                completionRewards(userId, pilgrimage.getPilgrimageId());
                created++;
            } else if (!done && pilgrimage.isCompleted()) {
                cancelCourse(userId, pilgrimage.getPilgrimageId());
                canceled++;
            } else {
                noop++;
            }
        }
        log.info("재집계. userId={}, created={}, canceled={}, noop={}", userId, created, canceled, noop);
        return new RecountResponse(created, canceled, noop);
    }

    /* ---------------- 내부 ---------------- */

    /** 완주 행을 세우고 인증서·전자책까지. 보상은 부르는 쪽이 이어서 한다(잠금 순서 ④). */
    private Certificate completeCourse(Long userId, Long pilgrimageId) {
        pilgrimageMapper.markCompleted(pilgrimageId);
        Certificate certificate = certificateService.issueForPilgrimage(userId, pilgrimageId);
        ebookService.enqueue(userId, pilgrimageId, Ebook.PILGRIMAGE);
        log.info("코스 완주. userId={}, pilgrimageId={}, serial={}",
                userId, pilgrimageId, certificate.getSerialNo());

        int completedCourses = pilgrimageMapper.countCompletedCourses(userId);
        // 전자일기장(INTERIM)은 여기서 넣지 않는다. 마일스톤 없이 한 번 넣으면 (사용자, INTERIM, 0)
        // 행이 먼저 자리를 잡아 3·6·9·12 가 영영 만들어지지 않는다 — completionRewards 한 곳에서만 넣는다.
        if (hoehyangHolds(completedCourses)) {
            certificateService.issueHoehyang(userId);
            ebookService.enqueue(userId, null, Ebook.HOEHYANG);
            log.info("전 코스 회향. userId={}", userId);
        }
        return certificate;
    }

    /**
     * 완주로 생기는 보상. 취소됐던 것이 있으면 새로 적립하지 않고 같은 행을 되살린다(보강 A §2-4).
     * 인증서는 새 번호로 나오는데 보상은 같은 행으로 돌아온다 — 번호는 밖에 나갔고 보상은 안 나갔기 때문이다.
     */
    private List<RewardResponse> completionRewards(Long userId, Long pilgrimageId) {
        List<RewardResponse> rewards = new ArrayList<>(
                rewardService.grantOrRestore(userId, RewardPolicy.ON_COURSE_COMPLETED, null, pilgrimageId));

        int completedCourses = pilgrimageMapper.countCompletedCourses(userId);
        // 3코스<b>마다</b> 전자일기장 한 권 — 3·6·9·12. 등호(== 3)로 두면 이미 4코스를 완주한 사람에게
        // 3 마일스톤 행이 없을 때 다시는 못 받는다(리뷰 2-3). >= 와 마일스톤 유니크가 함께 있어야
        // 다음 완주가 빠진 것을 스스로 채운다.
        if (completedCourses >= INTERIM_COURSE_COUNT && completedCourses % INTERIM_COURSE_COUNT == 0) {
            rewards.addAll(rewardService.grantOrRestore(
                    userId, RewardPolicy.ON_EVERY_THREE_COURSES, null, null, completedCourses));
            ebookService.enqueue(userId, null, Ebook.INTERIM, completedCourses);
            log.info("전자일기장 마일스톤. userId={}, milestone={}", userId, completedCourses);
        }
        if (hoehyangHolds(completedCourses)) {
            rewards.addAll(rewardService.grantOrRestore(
                    userId, RewardPolicy.ON_ALL_COMPLETED, null, pilgrimageId));
        }
        return rewards;
    }

    /**
     * 완주 취소 연쇄(챕터 7 §2-4).
     * <pre>
     *   완주 행 → 진행 중
     *   그 완주의 인증서 → REVOKED (지우지 않는다. 번호는 남아 "무효" 로 조회된다)
     *   그 완주의 보상 → GRANTED 만 REVOKED, 이미 움직인 것은 needs_review 만 켠다
     *   회향 조건이 깨졌으면 → 회향 인증서·회향 보상도 같은 규칙으로
     * </pre>
     * 전자책은 손대지 않는다. 완주 여부와 무관한 개인 소장물이다(§2-4 5번).
     */
    private void cancelCourse(Long userId, Long pilgrimageId) {
        pilgrimageMapper.markInProgress(pilgrimageId);
        certificateService.revokeForPilgrimage(pilgrimageId);
        rewardService.revokeOrFlagForPilgrimage(pilgrimageId);
        log.warn("완주 취소. pilgrimageId={}", pilgrimageId);

        // 코스 하나가 빠지면서 회향이 깨질 수 있다.
        if (!hoehyangHolds(pilgrimageMapper.countCompletedCourses(userId))) {
            certificateService.revokeHoehyang(userId);
            rewardService.revokeOrFlagByTrigger(userId, RewardPolicy.ON_ALL_COMPLETED);
        }
    }

    /**
     * 이 코스를 완주했는가. 슬롯 5칸이 살아 있는 도장으로 다 찼는지를 본다.
     * 진행률과 같은 계산을 쓴다 — 여기서 별도 SQL 로 다시 세면 두 값이 어긋난다(§2-1).
     */
    private boolean courseCompleted(Long userId, Long pilgrimageId) {
        Pilgrimage pilgrimage = pilgrimageMapper.findById(pilgrimageId);
        if (pilgrimage == null) {
            return false;
        }
        ProgressResponse progress = pilgrimageService.progressOf(userId, pilgrimage.getCourseId());
        return progress != null && progress.completedCount() >= ProgressResponse.SITES_PER_COURSE;
    }

    /**
     * 회향 성립 조건. 하한을 함께 본다 — "완주 수 >= 전체 ACTIVE 코스 수" 만 보면
     * ACTIVE 가 1개인 초기에 코스 하나를 끝낸 사람에게 회향 인증서와 실물 기념품이 나간다.
     * 되돌리기 어려운 종류라 하한이 없으면 안 된다(정리.md §6-8).
     */
    private boolean hoehyangHolds(int completedCourses) {
        int totalCourses = courseService.countActiveCourses();
        int floor = rewardProperties.hoehyangMinCourses();
        return totalCourses >= floor && completedCourses >= totalCourses && completedCourses >= floor;
    }
}
