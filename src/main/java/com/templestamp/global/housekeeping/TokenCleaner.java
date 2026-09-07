package com.templestamp.global.housekeeping;

import com.templestamp.auth.RefreshTokenMapper;
import com.templestamp.global.config.EbookProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 만료 토큰 정리(챕터 9 §5 ②).
 * <p>
 * 기준이 <b>만료 시각 + 유예</b>인 것이 요점이다. "폐기된 것을 지운다" 로 하면
 * 챕터 1 의 재사용 탐지가 눈을 잃는다 — 이미 쓴 토큰이 또 왔을 때 그 행이 있어야
 * "재사용" 이라고 말할 수 있고, 지워 버리면 그냥 "모르는 토큰" 이 된다(함정 6).
 * 만료된 토큰은 어차피 검증에서 막히므로 지워도 탐지에 구멍이 나지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleaner {

    private final RefreshTokenMapper refreshTokenMapper;
    private final EbookProperties ebookProperties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteExpired() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(ebookProperties.tokenGraceHours());
        int deleted = refreshTokenMapper.deleteExpiredBefore(cutoff, ebookProperties.tokenPerRun());
        if (deleted > 0) {
            log.info("만료된 리프레시 토큰 {}건 삭제(기준 {})", deleted, cutoff);
        }
        return deleted;
    }
}
