package com.templestamp.site;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사찰 간 최소 이동시간. 방향이 있는 쌍이다(A→B 와 B→A 가 다를 수 있다).
 * 직전 도장에서 이 시간보다 빨리 다음 도장을 찍으면 물리적으로 이동이 불가능하므로
 * 완료 대신 심사 보류로 보낸다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteDistance {

    private Long siteAId;
    private Long siteBId;
    private Integer minMinutes;
}
