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
import com.templestamp.reward.RewardPolicy;
import com.templestamp.reward.RewardService;
import com.templestamp.reward.dto.RewardResponse;
import com.templestamp.stamp.dto.CompletionResult;
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
 *     → 도장 보상 적립
 *     → 5칸 다 찼으면 코스 완주: 인증서 발급 · 코스본 대기열 · 코스 보상
 *       → 완주 코스가 3개면 중간본 대기열 · 3코스 보상
 *       → 모든 코스를 마쳤으면 회향 인증서 · 회향본 대기열 · 전체 완주 보상
 * </pre>
 * StampService 가 이 흐름을 직접 들고 있으면 GPS/QR/다짐 검증 사이에 완주 처리가 섞여
 * 나중에 어느 쪽을 고쳐도 다른 쪽이 깨진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompletionService {

    /** 중간 편집본을 만드는 완주 코스 수. */
    private static final int INTERIM_COURSE_COUNT = 3;

    private final PilgrimageMapper pilgrimageMapper;
    private final StampMapper stampMapper;
    private final CourseService courseService;
    private final CertificateService certificateService;
    private final EbookService ebookService;
    private final RewardService rewardService;
    private final RewardProperties rewardProperties;

    @Transactional
    public CompletionResult afterStampCompleted(Long userId, Long pilgrimageId, Long stampId) {
        List<RewardResponse> rewards = new ArrayList<>(
                rewardService.grant(userId, RewardPolicy.ON_STAMP_COMPLETED, stampId, null));

        int completedCount = stampMapper.countCompleted(pilgrimageId);
        if (completedCount < ProgressResponse.SITES_PER_COURSE) {
            return CompletionResult.notCompleted(rewards);
        }

        // ---- 코스 완주 ----
        pilgrimageMapper.markCompleted(pilgrimageId);
        Certificate certificate = certificateService.issueForPilgrimage(userId, pilgrimageId);
        ebookService.enqueue(userId, pilgrimageId, Ebook.PILGRIMAGE);
        rewards.addAll(rewardService.grant(
                userId, RewardPolicy.ON_COURSE_COMPLETED, null, pilgrimageId));

        log.info("코스 완주. userId={}, pilgrimageId={}, serial={}",
                userId, pilgrimageId, certificate.getSerialNo());

        // ---- 3코스 중간본 ----
        int completedCourses = pilgrimageMapper.countCompletedCourses(userId);
        if (completedCourses == INTERIM_COURSE_COUNT) {
            ebookService.enqueue(userId, null, Ebook.INTERIM);
            rewards.addAll(rewardService.grant(
                    userId, RewardPolicy.ON_THREE_COURSES_COMPLETED, null, pilgrimageId));
        }

        // ---- 전 코스 회향 ----
        // 하한을 함께 본다. "완주 수 >= 전체 ACTIVE 코스 수" 만 보면 ACTIVE 가 1개인 초기에
        // 코스 하나를 끝낸 사람에게 회향 인증서와 실물 기념품이 나간다 — 되돌리기 어려운 종류다.
        int totalCourses = courseService.countActiveCourses();
        if (totalCourses >= rewardProperties.hoehyangMinCourses() && completedCourses >= totalCourses) {
            certificateService.issueHoehyang(userId);
            ebookService.enqueue(userId, null, Ebook.HOEHYANG);
            rewards.addAll(rewardService.grant(
                    userId, RewardPolicy.ON_ALL_COMPLETED, null, pilgrimageId));
            log.info("전 코스 회향. userId={}", userId);
        }

        return new CompletionResult(true,
                certificate.getCertificateId(), certificate.getSerialNo(), rewards);
    }

    /**
     * 심사 반려 등으로 완료 도장이 줄었을 때 되돌린다.
     * 인증서는 회수한다 — 그 번호가 제3자에게 이미 제시됐을 수 있어 즉시 무효가 드러나야 한다.
     * 이미 적립된 보상은 건드리지 않는다. 지급된 물건을 시스템이 스스로 회수할 수는 없고,
     * 그 판단은 사람이 해야 한다.
     */
    @Transactional
    public void afterStampRevoked(Long pilgrimageId) {
        Pilgrimage pilgrimage = pilgrimageMapper.findById(pilgrimageId);
        if (pilgrimage == null) {
            throw new BusinessException(ErrorCode.PILGRIMAGE_4040);
        }

        int completedCount = stampMapper.countCompleted(pilgrimageId);
        if (completedCount < ProgressResponse.SITES_PER_COURSE && pilgrimage.isCompleted()) {
            pilgrimageMapper.markInProgress(pilgrimageId);
            certificateService.revokeForPilgrimage(pilgrimageId);
            log.warn("완주 취소. pilgrimageId={}, 완료={}/{}",
                    pilgrimageId, completedCount, ProgressResponse.SITES_PER_COURSE);
        }
    }
}
