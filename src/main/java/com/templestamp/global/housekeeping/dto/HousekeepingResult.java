package com.templestamp.global.housekeeping.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 청소기 한 바퀴의 결과(챕터 9 §5). 로그 한 줄과 관리자 수동 실행 응답이 같은 숫자를 쓴다 —
 * 두 곳이 다른 값을 말하면 어느 쪽을 믿을지 사람이 매번 판단해야 한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class HousekeepingResult {
    private int orphanDeleted;
    private int orphanFailed;
    private int tokenDeleted;
    private int ebookReady;
    private int ebookFailed;
    private int sessionExpired;
}
