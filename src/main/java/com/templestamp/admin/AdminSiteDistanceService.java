// src/main/java/com/templestamp/admin/AdminSiteDistanceService.java
package com.templestamp.admin;

import com.templestamp.admin.dto.SiteDistanceSaveRequest;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.site.SiteDistanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * site_distance UPSERT. 이 표는 응답 DTO 가 없다 — 최소 이동시간이 밖으로 나가면
 * "그 시간만큼 기다렸다가 찍으면 된다" 가 되어 심사 자체가 우회된다. 쓰기만 한다.
 */
@Service
@RequiredArgsConstructor
public class AdminSiteDistanceService {

    private final SiteDistanceMapper siteDistanceMapper;

    @Transactional
    public void upsertAll(SiteDistanceSaveRequest req) {
        for (var it : req.items()) {
            if (it.siteAId().equals(it.siteBId())) {
                throw new BusinessException(ErrorCode.COMMON_4000, "출발 사찰과 도착 사찰이 같습니다.");
            }
            siteDistanceMapper.upsert(it.siteAId(), it.siteBId(), it.minMinutes());
            // 없는 사찰이면 FK 위반 → DataIntegrityViolationException → 핸들러가 처리한다.
        }
    }
}
