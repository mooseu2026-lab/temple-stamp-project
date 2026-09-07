// src/main/java/com/templestamp/pilgrimage/PilgrimageMapper.java
package com.templestamp.pilgrimage;

import com.templestamp.pilgrimage.dto.PassportSlotRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper                                                        // @MapperScan(annotationClass = Mapper.class) 이 이 표시를 보고 빈으로 만든다
public interface PilgrimageMapper {
    Pilgrimage findByUserAndCourse(@Param("userId") Long userId, @Param("courseId") Long courseId);   // uk 기준 1건 또는 null
    Pilgrimage findById(@Param("pilgrimageId") Long pilgrimageId);
    /**
     * 중복 키로 INSERT 가 막힌 뒤의 재조회 전용. 잠금 읽기라 <b>다른 트랜잭션이 방금 커밋한 행</b>이 보인다.
     * 평범한 SELECT 는 MySQL 기본 격리수준(REPEATABLE READ)에서 이 트랜잭션의 첫 스냅샷을 보므로
     * 여전히 "없음"(null)을 돌려준다 — 그러면 경쟁에서 진 쪽이 NPE 로 500 을 받는다.
     */
    Pilgrimage findByUserAndCourseForUpdate(@Param("userId") Long userId, @Param("courseId") Long courseId);
    int insert(Pilgrimage pilgrimage);                         // useGeneratedKeys → pilgrimageId. 동시 연타는 uk 가 막고 Service 가 재조회
    int countCompletedStamps(@Param("pilgrimageId") Long pilgrimageId);   // ProgressResponse.completedCount 의 유일한 출처
    List<PassportSlotRow> findPassportSlots(@Param("userId") Long userId, @Param("locale") String locale);   // 여권 전체 — ACTIVE 코스만

    /* 교재 [기본 40] 에는 없다. 챕터 7 CompletionService 가 쓰므로 남긴다 — 완주 전이·취소와 그 집계. */
    int markCompleted(@Param("pilgrimageId") Long pilgrimageId);
    int markInProgress(@Param("pilgrimageId") Long pilgrimageId);
    int countCompletedCourses(@Param("userId") Long userId);

    /** 재집계 — 그 사람이 시작한 모든 코스. 완주 여부를 코스마다 다시 센다(챕터 7 §2-2). */
    List<Pilgrimage> findByUserId(@Param("userId") Long userId);

    /** 전체 재집계 — 순례를 하나라도 시작한 사람들. 걷지 않은 사람은 셀 것이 없다. */
    List<Long> findUserIdsWithPilgrimage();
}
