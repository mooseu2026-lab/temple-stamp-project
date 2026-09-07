package com.templestamp.stamp;

/**
 * 관리자가 도장을 승인·반려한 사실. 트랜잭션이 <b>커밋된 뒤</b> 완주 재집계를 태우기 위한 신호다.
 *
 * @param userId       도장 주인
 * @param pilgrimageId 그 도장이 속한 순례(= 그 사용자의 그 코스)
 * @param stampId      승인된 도장. 반려면 쓰지 않는다
 * @param approved     승인이면 true
 */
public record StampReviewedEvent(Long userId, Long pilgrimageId, Long stampId, boolean approved) {
}
