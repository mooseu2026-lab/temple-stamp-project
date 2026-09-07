package com.templestamp.course;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 코스 안의 자리. position 이 여권의 칸 번호이고, verseNo 가 그 칸의 오관게 구절이다.
 * <p>
 * site_id 에 UNIQUE 가 걸려 있어 사찰 하나는 정확히 한 코스의 한 자리에만 속한다.
 * 그래서 사찰 ID 하나만 알면 코스·자리·구절이 전부 결정된다 —
 * 싱글페이지와 GPS 인증이 siteId 만 받는 근거다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseSite {

    private Long courseSiteId;
    private Long courseId;
    private Long siteId;
    private Integer position;
    private Integer verseNo;
}
