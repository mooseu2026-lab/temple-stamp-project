package com.templestamp.stamp.dto;

/**
 * 한 사람의 완주 재집계 결과.
 * 세 수를 나눠 주는 이유는 "아무 일도 없었다"(noop)와 "고칠 게 있었다"(created·canceled)를
 * 관리자가 구분할 수 있어야 하기 때문이다 — 두 번째 실행부터는 셋 다 noop 여야 정상이다.
 * [사용 위치] CompletionService → AdminCompletionController
 */
public record RecountResponse(int created, int canceled, int noop) {
}
