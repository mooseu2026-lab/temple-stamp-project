// src/main/java/com/templestamp/course/SlotSiteMapper.java
package com.templestamp.course;

import com.templestamp.course.dto.SlotCandidateRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface SlotSiteMapper {
    int upsert(SlotSite s);                                                                  // uk(course_site_id, site_id, track)
    boolean exists(@Param("courseSiteId") Long courseSiteId, @Param("siteId") Long siteId);  // GPS 1단계 검증 — track 무관

    /** 그 (사찰, 트랙) 을 이미 쓰고 있는 자리. uk_slot_site_site 를 코드에서 먼저 확인하는 데 쓴다. */
    Optional<Long> findOwnerCourseSiteId(@Param("siteId") Long siteId, @Param("track") String track);
    List<SlotCandidateRow> findByCourse(@Param("courseId") Long courseId, @Param("locale") String locale);

    /** Q3: 과포화 사찰을 공개 응답에서 감춘다. 시더가 note 의 [Q3 확정] 을 보고 켠다. */
    int markCongested(@Param("siteId") Long siteId);
}
