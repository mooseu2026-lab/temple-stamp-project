package com.templestamp.stamp;

import com.templestamp.global.config.StampProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 진행 중인 채로 방치된 도장을 정리한다. <b>두 경로가 함께 돈다.</b>
 * <p>
 * ① {@link #expire(Long)} — 사용자가 만료된 세션으로 다시 찾아온 그 순간. {@code REQUIRES_NEW} 다.
 * {@link StampService} 가 곧 409(만료)를 던져 자기 트랜잭션을 롤백하므로, 같은 트랜잭션에서 EXPIRED 로 바꾸면
 * 그 기록까지 되돌아간다(정리.md §6-2). {@code @Transactional} 은 프록시라 <b>같은 클래스 안에서 부르면 안 걸린다</b> —
 * 그래서 이 클래스가 따로 있어야 한다.
 * <p>
 * ② {@link #expireStale()} — 5분마다 도는 청소기. 만료된 뒤 <b>다시 오지 않는</b> 세션은 ①이 영원히 불리지 않으므로
 * 이쪽만 닫을 수 있다. 관리자 목록·통계가 GPS_DONE 인 유령 행으로 오염되는 것을 막는다.
 * <p>
 * GPS 만 찍고 QR 을 안 찍은 세션을 계속 살려 두면 사찰을 떠난 뒤에 QR 사진만 받아 통과하는 길이 열린다.
 * 그래서 60분이 지나면 EXPIRED 로 닫고 처음부터 다시 하게 한다.
 * 만료된 도장은 GPS 확인을 다시 통과하면 같은 행이 재사용된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StampExpireService {

    /** 한 묶음의 크기. 잠금을 쥐는 시간과 왕복 횟수 사이의 선이다. */
    private static final int BATCH_SIZE = 200;
    /** 한 바퀴의 묶음 상한 — 200 × 100 = 20,000건. 나머지는 다음 바퀴가 가져간다. */
    private static final int MAX_ROUNDS = 100;

    private final StampMapper stampMapper;
    private final StampProperties stampProperties;
    private final StampExpireBatch stampExpireBatch;

    /**
     * 세션이 지났으면 그 한 건을 EXPIRED 로 닫고 {@code true} 를 돌려준다.
     * <b>부모 트랜잭션과 무관한 새 트랜잭션</b>이라 여기서 커밋되면 부모가 롤백돼도 남는다.
     * 이미 COMPLETED·PENDING 이거나 아직 만료가 아닌 행은 SQL 의 WHERE 가 막는다.
     * <p>
     * <b>★ 부모가 이 행을 잠그기 전에 불러야 한다.</b> 부모가 {@code FOR UPDATE} 로 잠근 뒤에 부르면
     * 새 트랜잭션이 부모의 잠금을 기다리다 자기 자신과 교착한다(정리.md §6-7).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expireIfStale(Long stampId, int sessionMinutes) {
        boolean expired = stampMapper.markExpiredIfStale(stampId, sessionMinutes) > 0;
        if (expired) {
            log.info("세션 만료로 닫음. stampId={}", stampId);
        }
        return expired;
    }

    /**
     * 오래 열려 있는 도장 세션을 닫는다. <b>PK 순으로 200건씩</b> 끊어 닫고, 묶음마다 트랜잭션이 따로다.
     * <p>
     * 스케줄 애너테이션이 여기 없는 이유 — 챕터 9 에서 청소기 하나(HousekeepingScheduler)로 모았다.
     * 스케줄러가 둘이면 "무엇이 언제 도는가" 를 두 곳에서 읽어야 한다.
     * <p>
     * <b>한 번의 UPDATE 로 조건을 스캔하며 닫지 않는다.</b> 그렇게 하면 보조 색인을 먼저,
     * PRIMARY 를 나중에 잠근다. 미션 제출(markCompleted)은 PRIMARY 를 먼저 잠그므로 순서가 엇갈려
     * 교착이 나고, 진 쪽이 사용자 요청이라 500 이 나갔다 — 최종 점검 F 에서 만료 대상 3,000건을 두고
     * 동시 20 으로 60초 돌려 교착 8건·500 5건을 실측했다. 그래서 고르기(잠그지 않음)와
     * 갱신(PK 로만 잠금)을 나눴다(정리.md §6-24).
     * <p>
     * 이 메서드 자체에는 트랜잭션이 없다. 있으면 묶음마다 커밋되지 않고 하나로 묶여
     * 끊어 닫는 의미가 사라진다.
     */
    public int expireStale() {
        int sessionMinutes = stampProperties.sessionMinutes();
        long afterId = 0;
        int expired = 0;

        // 한 바퀴에 도는 상한. 밀린 것이 아무리 많아도 청소기 한 바퀴가 끝나지 않는 일은 없어야 한다.
        for (int round = 0; round < MAX_ROUNDS; round++) {
            List<Long> ids = stampMapper.findStaleIds(sessionMinutes, afterId, BATCH_SIZE);
            if (ids.isEmpty()) {
                break;
            }
            expired += stampExpireBatch.expireBatch(ids);
            afterId = ids.get(ids.size() - 1);   // 앞으로만 간다 — 0행인 묶음이 나와도 같은 자리를 다시 읽지 않는다
            if (ids.size() < BATCH_SIZE) {
                break;
            }
        }

        if (expired > 0) {
            log.info("만료 처리된 도장 {}건", expired);
        }
        return expired;
    }
}
