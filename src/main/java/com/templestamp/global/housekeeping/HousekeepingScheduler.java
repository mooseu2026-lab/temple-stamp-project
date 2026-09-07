package com.templestamp.global.housekeeping;

import com.templestamp.global.housekeeping.dto.HousekeepingResult;
import com.templestamp.stamp.StampExpireService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.function.IntSupplier;

/**
 * 청소기 — 5분마다 도는 <b>단 하나의</b> 스케줄러(챕터 9 §5).
 * <p>
 * 스케줄러를 하나로 모은 이유는 "무엇이 언제 도는가" 를 한 곳에서 읽게 하기 위해서다.
 * 챕터 5 의 도장 세션 만료와 챕터 1 의 토큰 정리가 각자 돌고 있었고, 그중 하나(토큰)는
 * 주기가 수명보다 짧아 <b>실제로는 한 번도 지운 적이 없었다</b> — 점검 E 가 사용자 8명에
 * 1,674행을 실측한 것이 그 결과다.
 * <p>
 * <b>작업마다 트랜잭션이 따로다.</b> 하나가 실패해도 나머지는 돈다 — 저장소가 잠깐 아프다는
 * 이유로 도장 세션이 영원히 열려 있으면 안 된다(함정 10). 그래서 각 작업을 다른 빈의
 * {@code REQUIRES_NEW} 메서드로 부르고, 여기서는 예외를 삼킨다.
 * <p>
 * <b>단일 인스턴스 전제다.</b> 여러 대로 늘리면 같은 일을 두 번 하게 된다 —
 * 전자책은 있는 행을 {@code FOR UPDATE} 로 잠가 막지만, 나머지는 중복 실행이 무해할 뿐
 * 막혀 있지는 않다. 분산 잠금(ShedLock)은 정리.md §7 미결로 남겼다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HousekeepingScheduler {

    private final OrphanCleaner orphanCleaner;
    private final TokenCleaner tokenCleaner;
    private final EbookBuilderRunner ebookBuilderRunner;
    private final StampExpireService stampExpireService;

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    public void scheduled() {
        run();
    }

    /**
     * 한 바퀴. 관리자 수동 실행도 이것을 부른다 — 테스트와 운영 복구에서
     * "5분을 기다리는" 것 말고 다른 길이 있어야 한다.
     */
    public HousekeepingResult run() {
        HousekeepingResult result = new HousekeepingResult();
        safely("orphan", () -> {
            OrphanCleaner.Counts c = orphanCleaner.consume();
            result.setOrphanDeleted(c.deleted());
            result.setOrphanFailed(c.failed());
            return c.deleted();
        });
        safely("token", () -> {
            result.setTokenDeleted(tokenCleaner.deleteExpired());
            return result.getTokenDeleted();
        });
        safely("ebook", () -> {
            EbookBuilderRunner.Counts c = ebookBuilderRunner.buildRequested();
            result.setEbookReady(c.ready());
            result.setEbookFailed(c.failed());
            return c.ready();
        });
        safely("session", () -> {
            result.setSessionExpired(stampExpireService.expireStale());
            return result.getSessionExpired();
        });

        log.info("housekeeping orphan={}/{} token={} ebook={}/{} session={}",
                result.getOrphanDeleted(), result.getOrphanFailed(),
                result.getTokenDeleted(),
                result.getEbookReady(), result.getEbookFailed(),
                result.getSessionExpired());
        return result;
    }

    /**
     * 한 작업의 실패가 다음 작업을 막지 않게 한다. 여기서 예외를 삼키는 것이 맞는 몇 안 되는 자리다 —
     * 부르는 쪽이 스케줄러라 위로 올려 봐야 받을 사람이 없고, 다음 바퀴에 다시 시도된다.
     */
    private void safely(String step, IntSupplier work) {
        try {
            work.getAsInt();
        } catch (Exception e) {
            log.warn("housekeeping step 실패: {} — 나머지 작업은 계속한다", step, e);
        }
    }
}
