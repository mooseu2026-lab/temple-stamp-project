package com.templestamp.stamp.dto;

import java.util.List;

/**
 * 전체 재집계 결과. 사용자 한 명이 실패해도 나머지는 그대로 반영된다 —
 * 그래서 실패한 사용자 번호를 따로 돌려준다. 이 목록이 비어 있지 않으면
 * 그 사람들만 1명 재집계로 다시 돌리면 된다.
 * [사용 위치] CompletionBatchService → AdminCompletionController
 */
public record BatchRecountResponse(int scanned, int created, int canceled, List<Long> failed) {
}
