package com.templestamp.global.housekeeping;

import com.templestamp.ebook.EbookMapper;
import com.templestamp.ebook.EbookService;
import com.templestamp.global.config.EbookProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 대기 중인 전자책을 만든다(챕터 9 §5 ③).
 * <p>
 * 목록을 읽는 것과 한 권을 만드는 것을 <b>다른 트랜잭션</b>으로 나눈다.
 * 한 권이 실패해도 나머지가 만들어져야 하고, 세 권을 한 트랜잭션에 묶으면
 * 마지막 한 권의 실패가 앞의 두 권을 되돌린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EbookBuilderRunner {

    private final EbookMapper ebookMapper;
    private final EbookService ebookService;
    private final EbookProperties ebookProperties;

    public record Counts(int ready, int failed) {
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Counts buildRequested() {
        List<Long> ids = ebookMapper.findRequestedIds(ebookProperties.buildPerRun());
        int ready = 0;
        int failed = 0;
        for (Long id : ids) {
            // build() 가 자기 트랜잭션에서 있는 행을 잠근다 — 스케줄러가 둘이어도 한 번만 만들어진다.
            if (ebookService.build(id)) {
                ready++;
            } else {
                failed++;
            }
        }
        return new Counts(ready, failed);
    }
}
