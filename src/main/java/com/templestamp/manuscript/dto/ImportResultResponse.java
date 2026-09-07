package com.templestamp.manuscript.dto;

import java.util.List;

/**
 * CSV 반입 결과.
 * <p>
 * {@code wouldInsert} 와 {@code inserted} 를 나눠 둔 이유는 드라이런 때문이다 —
 * 드라이런은 "넣으면 몇 건이 들어간다" 만 알려 주고 실제로는 한 줄도 넣지 않는다.
 */
public record ImportResultResponse(boolean ok, int wouldInsert, int inserted, List<ImportError> errors) {
}
