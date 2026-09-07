package com.templestamp.course;

import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자리(course_site)에 후보 사찰 하나를 붙이는 <b>유일한 쓰기 통로</b>(챕터 11 항목 14).
 * <p>
 * 시더도 관리자도 여기를 지난다. 한 곳으로 모은 이유는 {@code uk_slot_site_site (site_id, track)}
 * 때문이다 — 같은 사찰이 같은 트랙으로 두 자리에 들어가면 한 사람이 같은 절에서 도장을 두 번 받는다.
 * <p>
 * <b>DB 유니크만 믿을 수 없다.</b> {@code SlotSiteMapper.upsert} 는
 * {@code ON DUPLICATE KEY UPDATE} 라, 다른 자리가 이미 그 (사찰, 트랙) 을 가지고 있으면
 * 예외가 아니라 <b>남의 행을 조용히 고치고</b> 성공을 돌려준다. 부른 쪽은 후보를 붙였다고 믿는데
 * 행은 여전히 앞 자리의 것이다. 그래서 넣기 전에 주인을 확인한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlotSiteService {

    private final SlotSiteMapper slotSiteMapper;

    /**
     * 후보를 붙인다. 같은 자리에 다시 부르면 부가 정보만 갱신된다(멱등 — 시더 215행 재실행).
     *
     * @throws BusinessException 다른 자리가 그 (사찰, 트랙) 을 이미 쓰고 있으면 409 COURSE-4093
     */
    @Transactional
    public int assign(SlotSite slotSite) {
        Long owner = slotSiteMapper.findOwnerCourseSiteId(slotSite.getSiteId(), slotSite.getTrack())
                .orElse(null);
        if (owner != null && !owner.equals(slotSite.getCourseSiteId())) {
            throw new BusinessException(ErrorCode.COURSE_4093,
                    "그 사찰은 같은 트랙의 다른 자리에 이미 후보로 들어가 있습니다.");
        }
        return slotSiteMapper.upsert(slotSite);
    }
}
