package com.templestamp.meditation;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 카테고리별 명상 개수. meditation 테이블에 없는 집계값이라 도메인 클래스로는 받을 수 없다.
 * [사용 위치] MeditationMapper 의 resultType
 */
@Getter
@Setter
@NoArgsConstructor
public class CategoryCountRow {
    private Integer categoryNo;
    private Integer count;
}
