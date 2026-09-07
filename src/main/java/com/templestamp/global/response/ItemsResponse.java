package com.templestamp.global.response;

import java.util.List;

/**
 * 페이징이 필요 없는 소규모 목록을 감싸는 공통 응답 DTO Record입니다.
 * 배열을 최상위로 내보내지 않고 객체로 감싸 이후 필드 추가에 대비합니다.
 * [사용 위치] 권역 목록·구절 목록·인증서 목록 등 소규모 목록 API
 */
public record ItemsResponse<T>(
        List<T> items
) {
    public static <T> ItemsResponse<T> of(List<T> items) {
        return new ItemsResponse<>(items == null ? List.of() : items);
    }
}
