package com.templestamp.course;

import com.templestamp.course.dto.CourseRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface CourseMapper {

    /** 노출 중인(ACTIVE) 코스만. regionId 가 null 이면 전체. */
    List<CourseRow> findActive(@Param("regionId") Long regionId);

    Optional<CourseRow> findRowById(@Param("courseId") Long courseId);

    /**
     * 공개용 상세 조회. ACTIVE 만 본다 — 목록에서 내린 코스가 직접 링크로 살아 있으면
     * "내렸다" 가 거짓이 된다. 관리자는 findRowById(status 무관)를 계속 쓴다.
     */
    Optional<CourseRow> findActiveRowById(@Param("courseId") Long courseId);

    Optional<Course> findById(@Param("courseId") Long courseId);

    int save(Course course);

    int update(Course course);

    /** 전체 코스 수. 회향(전 코스 완주) 판정에 쓴다. */
    int countActive();

    /** 상태만 바꾼다. 구성(자리 5곳)은 건드리지 않는다. */
    int updateStatus(@Param("courseId") Long courseId, @Param("status") String status);

    /** 걷는 중인 순례 수. 0 이 아니면 코스 구성을 바꿀 수 없다. */
    int countInProgressPilgrimages(@Param("courseId") Long courseId);

    /** 순례 시작 가능한 코스인가. 없음·DRAFT·INACTIVE 를 한 번에 거른다(교재 [기본 41]). */
    boolean existsActive(@Param("courseId") Long courseId);

    /** v4 시더 — 라인별 코스를 이름으로 찾는다(재실행 시 중복 생성 방지). */
    Long findIdByRegionAndName(@Param("regionId") Long regionId, @Param("name") String name);
}
