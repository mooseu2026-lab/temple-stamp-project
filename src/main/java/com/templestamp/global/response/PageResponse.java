package com.templestamp.global.response;

import java.util.List;

/**
 * 페이징이 필요한 목록을 담아 내려주는 공통 응답 DTO Record입니다.
 * [사용 위치] 생각상자·보상·인쇄 신청 등 목록 API 의 Service 조립 단계
 */
public record PageResponse<T>(
        List<T> items,
        int page,          // 0부터 시작
        int size,
        long totalCount,
        boolean hasNext
) {
    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalCount) {
        boolean hasNext = (long) (page + 1) * size < totalCount;
        return new PageResponse<>(items == null ? List.of() : items, page, size, totalCount, hasNext);
    }
}
