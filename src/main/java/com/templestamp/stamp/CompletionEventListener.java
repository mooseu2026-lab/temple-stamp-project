package com.templestamp.stamp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 도장 심사 뒤의 완주 재집계를 <b>도장 트랜잭션 밖에서</b> 돌린다(챕터 7 §2-3).
 * <p>
 * 심사는 도장 행을 잠근 채 진행된다. 그 안에서 완주·인증서·보상까지 잡으면 잠그는 행이 늘어나
 * 동시 승인 때 교착 후보가 된다 — 챕터 5 에서 실제로 겪은 자리다(정리.md §6-7).
 * 그래서 커밋 뒤(AFTER_COMMIT)에 새 트랜잭션(REQUIRES_NEW)으로 다시 시작한다.
 * <p>
 * AFTER_COMMIT 리스너는 같은 스레드에서 동기로 돈다 — 응답이 나가기 전에 끝나므로
 * "승인했는데 목록이 아직 안 바뀌었다" 는 경합은 생기지 않는다.
 * <p>
 * 여기서 터진 예외는 밖으로 내보내지 않는다. 도장 심사는 이미 커밋됐고, 예외를 올리면
 * 관리자에게는 500 이 보이면서 실제로는 심사가 된 상태가 된다. 대신 ERROR 로 남긴다 —
 * 재집계는 다시 돌리면 같은 결과가 나오는 작업이라(멱등) 뒤에 손으로 맞출 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompletionEventListener {

    private final CompletionService completionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStampReviewed(StampReviewedEvent event) {
        try {
            if (event.approved()) {
                completionService.afterStampCompleted(
                        event.userId(), event.pilgrimageId(), event.stampId());
            } else {
                completionService.afterStampRevoked(event.pilgrimageId());
            }
        } catch (RuntimeException e) {
            log.error("도장 심사 뒤 완주 재집계 실패 — pilgrimageId={}, 승인={}. 재집계로 복구해야 한다.",
                    event.pilgrimageId(), event.approved(), e);
        }
    }
}
