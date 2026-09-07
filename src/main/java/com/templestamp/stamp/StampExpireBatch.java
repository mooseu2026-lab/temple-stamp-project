package com.templestamp.stamp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 세션 만료를 <b>한 묶음씩</b> 닫는다. 묶음마다 트랜잭션이 따로다.
 * <p>
 * 클래스를 따로 둔 이유는 {@code @Transactional} 이 프록시라 <b>같은 클래스 안에서 부르면 걸리지 않기</b>
 * 때문이다({@link StampExpireService} 가 같은 이유로 {@code StampService} 에서 떨어져 나왔다).
 * {@link StampExpireService#expireStale()} 가 여기를 반복해서 부르고, 한 묶음이 끝날 때마다 커밋된다.
 * <p>
 * 묶음을 나누는 것이 핵심이다 — 3,000건을 한 트랜잭션으로 닫으면 그동안 그 행들의 잠금을 전부 쥐고 있어
 * 같은 시간에 도장을 찍는 사용자가 기다리게 된다. 200건씩 끊으면 쥐는 시간이 짧아진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StampExpireBatch {

    private final StampMapper stampMapper;

    /**
     * 고른 id 들을 EXPIRED 로 닫고 실제로 바뀐 행 수를 돌려준다.
     * <b>PK 로만 잠근다</b> — 미션 제출과 잠금 순서를 맞추기 위한 것이고, 이 규칙이 이 클래스의 존재 이유다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int expireBatch(List<Long> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        return stampMapper.expireByIds(ids);
    }
}
