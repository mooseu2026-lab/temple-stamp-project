package com.templestamp.reward;

import com.templestamp.admin.dto.ClaimReviewRequest;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.reward.dto.ClaimRequest;
import com.templestamp.reward.dto.ClaimResponse;
import com.templestamp.reward.dto.RewardResponse;
import com.templestamp.reward.dto.AdminClaimRow;
import com.templestamp.reward.dto.RewardRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RewardService {

    /**
     * 수령 신청을 받는 보상 종류. 챕터 11 이후 살아 있는 PHYSICAL 은 맞춤형 특별앨범 하나뿐이다
     * (전자일기장은 DIGITAL — 받는 순간 개인 소장이라 신청·배송이 없다).
     */
    private static final String PHYSICAL = "PHYSICAL";

    /**
     * 신청 버튼을 눌러도 되는 상태. 반려된 것은 다시 신청할 수 있다(챕터 7 §4-2 —
     * 반려는 끝이 아니라 "이번 신청이 안 됐다" 는 뜻이다).
     */
    private static final Set<String> CLAIMABLE_STATUSES = Set.of(UserReward.GRANTED, UserReward.REJECTED);

    private final RewardPolicyMapper policyMapper;
    private final UserRewardMapper userRewardMapper;
    private final RewardClaimMapper rewardClaimMapper;
    private final AuditScorer auditScorer;

    /**
     * claimable 은 <b>여기 한 곳에서만</b> 계산한다(챕터 7 §4-3). 응답 DTO 가 저마다 계산하면
     * 목록의 claimable 과 신청 API 의 판정이 어긋나 "버튼은 켜져 있는데 눌러도 안 되는" 상태가 된다.
     */
    public static boolean claimable(RewardRow row, Long viewerId) {
        return PHYSICAL.equals(row.getRewardType())
                // 꺼진 정책의 과거 행은 보여 주기만 한다 — 이제 와서 배송을 받아 줄 수 없다.
                && !row.isLegacy()
                && CLAIMABLE_STATUSES.contains(row.getStatus())
                && row.getUserId() != null && row.getUserId().equals(viewerId);
    }

    public List<RewardResponse> getRewards(Long userId) {
        return userRewardMapper.findRowsByUserId(userId).stream()
                .map(row -> RewardResponse.from(row, claimable(row, userId)))
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
            // 사용자 단위 보상은 여기서도 코스 id 를 비운다 — 두 경로가 다른 키를 쓰면 유니크가 헛돈다.
            boolean userScoped = policy.isUserScoped();
            reward.setStampId(stampScoped ? stampId : null);
            reward.setPilgrimageId((stampScoped || userScoped) ? null : pilgrimageId);

            if (userRewardMapper.grant(reward) == 0) {
                continue;   // 이미 적립돼 있었다
            }
            userRewardMapper
                    .findRow(policy.getRewardPolicyId(), userId,
                            reward.getStampId(), reward.getPilgrimageId(), reward.getMilestone())
                    .map(row -> RewardResponse.from(row, claimable(row, userId)))
                    .ifPresent(granted::add);
        }
        return granted;
    }

    /**
     * 적립하거나, 완주 취소로 회수됐던 <b>같은 행을 되살린다</b>(챕터 7 보강 A §2-4).
     * <p>
     * 멱등 키 `(정책, 도장)`·`(정책, 순례)` 가 두 번째 적립을 막기 때문에, 취소됐던 완주가
     * 다시 성립해도 그냥 다시 적립하면 아무 일도 일어나지 않는다 — 인증서는 새 번호로 나오는데
     * 보상만 영영 REVOKED 로 남는다. 그래서 새로 넣지 못했으면 되살려 본다.
     * <p>
     * 돌려주는 것은 <b>이번에 새로 생겼거나 되살아난 것</b>뿐이다. 그대로 있던 보상은 담지 않는다.
     */
    @Transactional
    public List<RewardResponse> grantOrRestore(Long userId, String triggerType,
                                               Long stampId, Long pilgrimageId) {
        return grantOrRestore(userId, triggerType, stampId, pilgrimageId, null);
    }

    /**
     * 마일스톤이 있는 보상(전자일기장 3·6·9·12)까지 받는 쪽.
     * 사용자 단위 보상은 <b>코스가 아니라 사람</b>으로 묶인다 — 코스 id 로 묶으면 반려·재승인에서 행이 는다.
     */
    @Transactional
    public List<RewardResponse> grantOrRestore(Long userId, String triggerType,
                                               Long stampId, Long pilgrimageId, Integer milestone) {
        List<RewardResponse> changed = new ArrayList<>();

        for (RewardPolicy policy : policyMapper.findByTrigger(triggerType)) {
            boolean stampScoped = policy.isStampScoped();
            boolean userScoped = policy.isUserScoped();
            Long scopedStampId = stampScoped ? stampId : null;
            // 사용자 단위면 코스 id 를 비운다. 그래야 user_key 생성 컬럼이 채워지고 유니크가 걸린다.
            Long scopedPilgrimageId = (stampScoped || userScoped) ? null : pilgrimageId;
            Integer scopedMilestone = userScoped ? milestone : null;

            UserReward reward = new UserReward();
            reward.setUserId(userId);
            reward.setRewardPolicyId(policy.getRewardPolicyId());
            reward.setStampId(scopedStampId);
            reward.setPilgrimageId(scopedPilgrimageId);
            reward.setMilestone(scopedMilestone);

            boolean touched = userRewardMapper.grant(reward) > 0;
            if (!touched) {
                touched = userRewardMapper.restoreRevoked(
                        policy.getRewardPolicyId(), userId, scopedStampId,
                        scopedPilgrimageId, scopedMilestone) > 0;
                if (touched) {
                    log.info("완주가 다시 성립해 보상을 되살렸다. userId={}, policy={}",
                            userId, policy.getRewardPolicyId());
                }
            }
            if (!touched) {
                continue;   // 이미 있고 회수된 적도 없다 — 건드릴 것이 없다
            }
            userRewardMapper
                    .findRow(policy.getRewardPolicyId(), userId, scopedStampId,
                            scopedPilgrimageId, scopedMilestone)
                    .map(row -> RewardResponse.from(row, claimable(row, userId)))
                    .ifPresent(changed::add);
        }
        return changed;
    }

    /**
     * 실물 보상 수령 신청. 응답의 claimable 을 믿지 않고 <b>서버가 다시 검사한다</b>(챕터 7 §4-3).
     * 프론트가 claimable 을 보지 않고 눌러도 여기서 막힌다 — 전체 점검 C 의 결함 5-1 이 그것이었다.
     * <p>
     * 검사 순서는 "있는가 → 내 것인가 → 신청할 수 있는 종류인가 → 지금 신청할 수 있는 상태인가" 다.
     * 남의 것을 먼저 걸러야 남의 보상 종류·상태가 오류 코드로 새어 나가지 않는다.
     * <p>
     * 감사 점수는 그대로 계산해 남긴다. 기본 설정에서는 차단하지 않고 기록만 하지만,
     * 점수가 높으면 바로 심사 대기로 보낸다.
     */
    @Transactional
    public ClaimResponse claim(Long userId, Long userRewardId, ClaimRequest request) {
        RewardRow row = userRewardMapper.findRowById(userRewardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REWARD_4040));

        // 남의 보상 — 저장소가 이미 쓰던 403 을 유지한다(교재 §4-3 의 "기존 403 유지" 갈래).
        if (!row.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.AUTH_4032);
        }
        if (!PHYSICAL.equals(row.getRewardType())) {
            throw new BusinessException(ErrorCode.REWARD_4001);
        }
        // 꺼진 정책의 과거 행. claimable 이 false 인 것을 서버도 같은 이유로 막는다.
        if (row.isLegacy()) {
            throw new BusinessException(ErrorCode.REWARD_4001);
        }
        if (!CLAIMABLE_STATUSES.contains(row.getStatus())) {
            throw new BusinessException(ErrorCode.REWARD_4092);
        }

        AuditScorer.Score score = auditScorer.score(userId, row.getPilgrimageId());
        String status = score.needsReview() ? UserReward.UNDER_REVIEW : UserReward.CLAIMED;

        // 신청할 수 있는 상태일 때만 바뀌는 UPDATE 라, 동시에 두 번 눌러도 한 번만 접수된다.
        if (userRewardMapper.claim(userRewardId, status, score.value(), score.detailJson()) == 0) {
            throw new BusinessException(ErrorCode.REWARD_4092);
        }

        // 배송 정보는 본체가 아니라 별도 표에 남긴다. 반려 뒤 다시 신청하면 덮어쓴다 —
        // 주소가 바뀌어서 다시 신청하는 경우가 있다.
        rewardClaimMapper.upsert(userRewardId, request.recipientName().trim(),
                request.phone().trim(), request.address().trim(), blankToNull(request.memo()));

        String message = score.needsReview()
                ? "확인이 필요해 검토 대기로 넘어갔습니다. 결과는 따로 알려 드립니다."
                : "수령 신청이 접수되었습니다. 심사 뒤 알려 드립니다.";
        // 접수된 순간부터 다시 누를 수 없다 — claimable 은 언제나 위 계산식 하나에서 나온다.
        return new ClaimResponse(userRewardId, status, false, message);
    }

    /* ---------------- 관리자 심사 ---------------- */

    public List<AdminClaimRow> getUnderReview() {
        return userRewardMapper.findUnderReview();
    }

    @Transactional
    public void review(Long adminId, Long userRewardId, ClaimReviewRequest request) {
        if (!request.isApprove() && (request.reason() == null || request.reason().isBlank())) {
            throw new BusinessException(ErrorCode.COMMON_4000, "거부 사유를 입력해 주세요.");
        }

        String status = request.isApprove() ? UserReward.PAID : UserReward.REJECTED;
        // 송장은 승인일 때만 남긴다. 반려에 송장이 실려 오면 조용히 버린다 — 보낸 물건이 없기 때문이다.
        String trackingNo = request.isApprove() ? blankToNull(request.trackingNo()) : null;
        // 신청 접수(CLAIMED)·검토 대기(UNDER_REVIEW) 가 아닌 것을 심사하려 하면 허용되지 않는 전이다.
        if (userRewardMapper.resolve(userRewardId, status, adminId, trackingNo) == 0) {
            throw new BusinessException(ErrorCode.REWARD_4090, "심사할 수 있는 상태가 아닙니다.");
        }
        log.info("보상 청구 {} — userRewardId={}, adminId={}",
                request.isApprove() ? "승인" : "거부", userRewardId, adminId);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /* ---------------- 완주 취소 연쇄 (챕터 7 §2-4) ---------------- */

    /**
     * 완주 하나가 취소됐을 때. <b>아직 아무도 움직이지 않은 GRANTED 만 회수</b>하고,
     * 신청·심사·지급까지 간 것은 상태를 그대로 두고 needs_review 만 켠다.
     * <p>
     * 실물 기념품은 이미 택배를 탔을 수 있다. 시스템이 그것까지 REVOKED 로 바꾸면
     * 장부는 깨끗해지지만 실제와 달라진다 — 그 판단은 사람이 해야 한다(함정 6).
     * <p>
     * <b>전자일기장은 이 그물에 걸리지 않는다</b>(챕터 11 결정 C). 사용자 단위 보상이라
     * {@code pilgrimage_id} 가 NULL 이고, 코스 하나가 취소돼도 이미 읽은 책을 뺏을 수는 없다.
     * 회수 대상은 {@code ALL_COMPLETED} 의 특별앨범뿐이다.
     */
    @Transactional
    public void revokeOrFlagForPilgrimage(Long pilgrimageId) {
        int revoked = userRewardMapper.revokeGrantedByPilgrimage(pilgrimageId);
        int flagged = userRewardMapper.flagNeedsReviewByPilgrimage(pilgrimageId);
        if (revoked + flagged > 0) {
            log.warn("완주 취소로 보상 정리. pilgrimageId={}, 회수={}, 사람이 볼 것={}",
                    pilgrimageId, revoked, flagged);
        }
    }

    /** 회향 보상은 특정 완주가 아니라 "사용자 + 트리거" 로 매인다. 규칙은 위와 같다. */
    @Transactional
    public void revokeOrFlagByTrigger(Long userId, String triggerType) {
        int revoked = userRewardMapper.revokeGrantedByTrigger(userId, triggerType);
        int flagged = userRewardMapper.flagNeedsReviewByTrigger(userId, triggerType);
        if (revoked + flagged > 0) {
            log.warn("조건이 깨져 보상 정리. userId={}, trigger={}, 회수={}, 사람이 볼 것={}",
                    userId, triggerType, revoked, flagged);
        }
    }
}
