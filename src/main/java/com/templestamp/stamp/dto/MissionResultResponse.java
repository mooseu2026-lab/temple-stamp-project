package com.templestamp.stamp.dto;

import com.templestamp.course.dto.ProgressResponse;
import com.templestamp.manuscript.dto.ExtPhraseResponse;
import com.templestamp.reward.dto.RewardResponse;

import java.util.List;

/**
 * 미션 제출 결과와 진행률·보상·완주 여부를 한 번에 내려주는 DTO Record입니다.
 * COMPLETED(정상 확정)와 PENDING(이동시간 미달 보류) 양쪽에 모두 사용합니다.
 *   진행률 → progress (ProgressResponse 통째 중첩)
 *   보상   → rewards (PENDING 이면 빈 배열)
 *   완주   → courseCompleted + certificateSerial (완주 아니면 null, 예: PG-2026-000012)
 *   문구   → extPhrase (발행 시점에 못 박은 확장문구. 없으면 null, 필드는 항상 있음 · 챕터 8 §3-2)
 * [사용 위치] StampService 가 CompletionResult 를 받아 조립 → Controller 반환
 */
public record MissionResultResponse(Long stampId, String status, String message,
                                    ProgressResponse progress, List<RewardResponse> rewards,
                                    boolean courseCompleted, String certificateSerial,
                                    ExtPhraseResponse extPhrase) {}
