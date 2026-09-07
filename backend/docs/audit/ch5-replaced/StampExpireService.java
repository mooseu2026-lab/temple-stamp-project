package com.templestamp.stamp;

import com.templestamp.global.config.StampProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 진행 중인 채로 방치된 도장을 정리한다.
 * <p>
 * GPS 만 찍고 QR 을 안 찍은 세션을 계속 살려 두면, 사찰을 떠난 뒤에 QR 사진만 받아 통과하는
 * 길이 열린다. 그래서 60분이 지나면 EXPIRED 로 닫고 처음부터 다시 하게 한다.
 * 만료된 도장은 GPS 확인을 다시 통과하면 같은 행이 재사용된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StampExpireService {

    private final StampMapper stampMapper;
    private final StampProperties stampProperties;

    /**
     * 5분마다. 세션 만료(60분)보다 촘촘하면 충분하다.
     * 기동 직후에 바로 돌면 DB 가 아직 안 뜬 환경에서 첫 로그가 예외로 시작하므로 1분 미룬다.
     */
    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    @Transactional
    public void expireStale() {
        int expired = stampMapper.expireStale(stampProperties.sessionMinutes());
        if (expired > 0) {
            log.info("만료 처리된 도장 {}건", expired);
        }
    }
}
