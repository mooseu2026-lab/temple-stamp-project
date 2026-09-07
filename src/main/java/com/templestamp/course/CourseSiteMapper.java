package com.templestamp.course;

import com.templestamp.course.dto.CourseSiteRow;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface CourseSiteMapper {

    List<CourseSiteRow> findByCourseId(@Param("courseId") Long courseId,
                                       @Param("locale") String locale);

    /**
     * 사찰 ID 하나로 자리를 찾는다. site_id 에 UNIQUE 가 걸려 있어 결과는 0 또는 1건이다.
     * 싱글페이지·GPS 인증이 siteId 만 받고도 코스·자리·구절을 알아내는 통로다.
     */
    Optional<CourseSiteRow> findBySiteId(@Param("siteId") Long siteId);

    Optional<CourseSiteRow> findRowById(@Param("courseSiteId") Long courseSiteId);

    int countByCourseId(@Param("courseId") Long courseId);

    /** 노출 중인 코스에 속한 전체 자리 수. 여권 요약의 분모다. */
    int countActiveSites();

    int deleteByCourseId(@Param("courseId") Long courseId);

    int insertAll(@Param("slots") List<CourseSite> slots);

    /** 이 사찰을 이미 가진 코스. uk_course_site_site 라 있어도 한 건이다. */
    Optional<Long> findCourseIdBySite(@Param("siteId") Long siteId);

    /** 이 코스에 배정된 사찰 중 ACTIVE 가 아닌 것의 수. 0 이어야 코스를 공개할 수 있다. */
    int countInactiveSites(@Param("courseId") Long courseId);

    /** v4 시더 — position(1~5) → course_site_id. @MapKey 가 List 를 Map 으로 바꿔 준다. */
    @MapKey("position")
    java.util.Map<Integer, java.util.Map<String, Object>> findSlotIdsByCourse(@Param("courseId") Long courseId);
}
