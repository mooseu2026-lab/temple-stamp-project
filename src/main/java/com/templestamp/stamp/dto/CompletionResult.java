package com.templestamp.stamp.dto;

import com.templestamp.reward.dto.RewardResponse;

import java.util.List;

/**
 * 완주 재집계 결과를 Service 사이에서 주고받는 DTO Record입니다.
 * 여러 테이블(pilgrimage·certificate·reward·ebook)을 건드린 결과라
 * 응답과 형태가 달라 별도로 둔 유일한 Result 타입입니다. Controller 밖으로 나가지 않습니다.
 * [사용 위치] CompletionService 반환 → StampService 가 MissionResultResponse 로 변환
 */
public record CompletionResult(
        boolean courseCompleted,
        Long certificateId,             // 완주 아니면 null
        String certificateSerial,       // certificate.serial_no, 예: PG-2026-000012. 완주 아니면 null
        List<RewardResponse> rewards    // 이번 확정으로 새로 발생한 보상. 없으면 빈 배열
) {
    public static CompletionResult notCompleted(List<RewardResponse> rewards) {
        return new CompletionResult(false, null, null, rewards == null ? List.of() : rewards);
    }
}
