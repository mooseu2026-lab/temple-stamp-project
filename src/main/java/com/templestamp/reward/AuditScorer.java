package com.templestamp.reward;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.templestamp.global.config.RewardProperties;
import com.templestamp.global.type.VerifyMethod;
import com.templestamp.stamp.StampMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 보상 부정 수령 위험도 채점.
 * <p>
 * 기본 설정(app.reward.audit-blocking=false)에서는 <b>점수를 기록만 하고 전부 통과시킨다.</b>
 * 순례는 실제로 산길을 걷는 활동이라, 통신이 나쁜 곳에서 예외 접수를 여러 번 쓰는 정상
 * 사용자가 흔하다. 임계치를 실제 데이터로 검증하기 전에 차단부터 켜면 그 사람들이 먼저 잘린다.
 * 점수가 쌓여 분포를 보고 나서 켜는 순서로 간다.
 * <p>
 * 점수가 낮을수록 정상이다. 근거는 audit_detail(JSON) 에 신호별로 남겨 심사 화면에 보여 준다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditScorer {

    private final StampMapper stampMapper;
    private final RewardProperties rewardProperties;
    private final ObjectMapper objectMapper;

    /** 채점 결과. detailJson 은 user_reward.audit_detail 에 그대로 들어간다. */
    public record Score(BigDecimal value, String detailJson, boolean needsReview) {
    }

    public Score score(Long userId, Long pilgrimageId) {
        List<Map<String, Object>> signals = new ArrayList<>();
        int total = 0;

        // 1) 예외 접수 비중. 전부 증빙으로 채운 완주는 현장 방문 근거가 약하다.
        int completed = stampMapper.countCompletedByUser(userId);
        int byEvidence = stampMapper.countByUserAndMethod(userId, VerifyMethod.EVIDENCE);
        if (completed > 0) {
            int ratio = byEvidence * 100 / completed;
            int points = ratio >= 80 ? 40 : ratio >= 50 ? 20 : 0;
            if (points > 0) {
                total += points;
                signals.add(signal("EVIDENCE_RATIO", points, "증빙 경로 비중 " + ratio + "%"));
            }
        }

        // 2) 도장 간 최소 간격. 이동이 물리적으로 불가능한 간격이면 신호로 잡는다.
        if (pilgrimageId != null) {
            Long minGap = stampMapper.findMinCompletionGapSeconds(pilgrimageId).orElse(null);
            long threshold = rewardProperties.minGapSeconds();
            if (minGap != null && minGap < threshold) {
                total += 35;
                signals.add(signal("SHORT_GAP", 35, "도장 최소 간격 " + minGap + "초"));
            }
        }

        // 3) 이동시간 미달로 보류된 이력. 한 번은 실수일 수 있어도 반복되면 다르다.
        int travelFlags = stampMapper.countTravelTimeFlags(userId);
        if (travelFlags > 0) {
            int points = Math.min(travelFlags * 15, 45);
            total += points;
            signals.add(signal("TRAVEL_TIME_FLAG", points, "이동시간 미달 보류 " + travelFlags + "건"));
        }

        int capped = Math.min(total, 100);
        boolean needsReview = rewardProperties.auditBlocking()
                && capped >= rewardProperties.reviewThreshold();

        if (capped > 0) {
            log.info("보상 이상 점수. userId={}, pilgrimageId={}, score={}, blocking={}",
                    userId, pilgrimageId, capped, needsReview);
        }
        return new Score(BigDecimal.valueOf(capped), toJson(capped, signals), needsReview);
    }

    private Map<String, Object> signal(String code, int points, String detail) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("points", points);
        map.put("detail", detail);
        return map;
    }

    private String toJson(int score, List<Map<String, Object>> signals) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("score", score);
        payload.put("signals", signals);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            // 채점 근거를 못 만든다고 보상 지급을 막을 이유는 없다.
            log.warn("audit_detail 직렬화 실패", e);
            return null;
        }
    }
}
