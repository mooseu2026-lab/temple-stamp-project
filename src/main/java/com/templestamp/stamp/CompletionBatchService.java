package com.templestamp.stamp;

import com.templestamp.pilgrimage.PilgrimageMapper;
import com.templestamp.stamp.dto.BatchRecountResponse;
import com.templestamp.stamp.dto.RecountResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 전체 재집계. <b>사용자 한 명이 한 트랜잭션</b>이다(챕터 7 §2-3 ③).
 * <p>
 * 이 클래스가 따로 있는 이유가 그것이다. 같은 클래스 안에서 {@code recount()} 를 부르면
 * 프록시를 거치지 않아 트랜잭션이 열리지 않는다 — 전부 한 트랜잭션이 되어 한 명이 실패할 때
 * 앞사람들의 결과까지 되돌아간다. 다른 빈을 거쳐야 사용자마다 트랜잭션이 새로 열린다.
 * <p>
 * 그래서 이 메서드에는 {@code @Transactional} 을 붙이지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompletionBatchService {

    private final CompletionService completionService;
    private final PilgrimageMapper pilgrimageMapper;

    public BatchRecountResponse recountAll() {
        List<Long> userIds = pilgrimageMapper.findUserIdsWithPilgrimage();
        List<Long> failed = new ArrayList<>();
        int created = 0, canceled = 0;

        for (Long userId : userIds) {
            try {
                RecountResponse result = completionService.recount(userId);
                created += result.created();
                canceled += result.canceled();
            } catch (RuntimeException e) {
                // 한 사람의 실패가 나머지를 되돌리지 않는다. 누가 실패했는지만 남기고 계속 간다.
                failed.add(userId);
                log.error("재집계 실패. userId={}", userId, e);
            }
        }
        log.info("전체 재집계. scanned={}, created={}, canceled={}, failed={}",
                userIds.size(), created, canceled, failed.size());
        return new BatchRecountResponse(userIds.size(), created, canceled, failed);
    }
}
