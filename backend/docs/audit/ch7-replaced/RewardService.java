package com.templestamp.reward;

import com.templestamp.admin.dto.ClaimReviewRequest;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.reward.dto.ClaimResponse;
import com.templestamp.reward.dto.RewardResponse;
import com.templestamp.reward.dto.RewardRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RewardService {

    private final RewardPolicyMapper policyMapper;
    private final UserRewardMapper userRewardMapper;
    private final AuditScorer auditScorer;

    public List<RewardResponse> getRewards(Long userId) {
        return userRewardMapper.findRowsByUserId(userId).stream()
                .map(RewardResponse::from)
                .toList();
    }

    /**
     * 트리거가 발생했을 때 해당 정책들을 적립한다.
     * 중복 적립은 DB 유니크 제약이 막고, INSERT IGNORE 라 재시도해도 조용히 넘어간다.
     *
     * @param stampId       도장 단위 보상의 근거. 코스 단위면 null
     * @param pilgrimageId  코스 단위 보상의 근거. 도장 단위면 null
     * @return 이번 호출로 새로 적립된 보상들
     */
    @Transactional
    public List<RewardResponse> grant(Long userId, String triggerType, Long stampId, Long pilgrimageId) {
        List<RewardResponse> granted = new ArrayList<>();

        for (RewardPolicy policy : policyMapper.findByTrigger(triggerType)) {
            boolean stampScoped = policy.isStampScoped();

            UserReward reward = new UserReward();
            reward.setUserId(userId);
            reward.setRewardPolicyId(policy.getRewardPolicyId());
            reward.setStampId(stampScoped ? stampId : null);
            reward.setPilgrimageId(stampScoped ? null : pilgrimageId);

            if (userRewardMapper.grant(reward) == 0) {
                continue;   // 이미 적립돼 있었다
            }
            userRewardMapper
                    .findRow(policy.getRewardPolicyId(), reward.getStampId(), reward.getPilgrimageId())
                    .map(RewardResponse::from)
                    .ifPresent(granted::add);
        }
        return granted;
    }

    /**
     * 실물 보상 청구. 감사 점수를 계산해 함께 남긴다.
     * <p>
     * 기본 설정(app.reward.audit-blocking=false)에서는 점수를 기록만 하고 전부 즉시 지급한다.
     * 임계치를 실제 데이터로 검증하기 전에 차단부터 켜면, 통신이 나쁜 곳에서 예외 접수를
     * 여러 번 쓴 정상 사용자가 먼저 잘린다.
     */
    @Transactional
    public ClaimResponse claim(Long userId, Long userRewardId) {
        UserReward reward = userRewardMapper.findById(userRewardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REWARD_4040));

        if (!reward.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.AUTH_4032);
        }
        if (!UserReward.GRANTED.equals(reward.getStatus())) {
            throw new BusinessException(ErrorCode.REWARD_4091);
        }

        AuditScorer.Score score = auditScorer.score(userId, reward.getPilgrimageId());
        String status = score.needsReview() ? UserReward.UNDER_REVIEW : UserReward.PAID;

        // GRANTED 일 때만 바뀌는 UPDATE 라, 동시에 두 번 눌러도 한 번만 청구된다.
        if (userRewardMapper.claim(userRewardId, status, score.value(), score.detailJson()) == 0) {
            throw new BusinessException(ErrorCode.REWARD_4091);
        }

        String message = score.needsReview()
                ? "확인이 필요해 검토 대기로 넘어갔습니다. 결과는 따로 알려 드립니다."
                : "보상이 지급되었습니다.";
        return new ClaimResponse(userRewardId, status, message);
    }

    /* ---------------- 관리자 심사 ---------------- */

    public List<RewardRow> getUnderReview() {
        return userRewardMapper.findUnderReview();
    }

    @Transactional
    public void review(Long adminId, Long userRewardId, ClaimReviewRequest request) {
        if (!request.isApprove() && (request.reason() == null || request.reason().isBlank())) {
            throw new BusinessException(ErrorCode.COMMON_4000, "거부 사유를 입력해 주세요.");
        }

        String status = request.isApprove() ? UserReward.PAID : UserReward.REJECTED;
        if (userRewardMapper.resolve(userRewardId, status, adminId) == 0) {
            throw new BusinessException(ErrorCode.COMMON_4090, "검토 대기 상태가 아닙니다.");
        }
        log.info("보상 청구 {} — userRewardId={}, adminId={}",
                request.isApprove() ? "승인" : "거부", userRewardId, adminId);
    }
}
