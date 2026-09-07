package com.templestamp.pilgrimage;

import com.templestamp.pilgrimage.dto.PassportSlotRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface PilgrimageMapper {

    int save(Pilgrimage pilgrimage);

    Optional<Pilgrimage> findById(@Param("pilgrimageId") Long pilgrimageId);

    Optional<Pilgrimage> findByUserAndCourse(@Param("userId") Long userId,
                                             @Param("courseId") Long courseId);

    List<Pilgrimage> findByUserId(@Param("userId") Long userId);

    int markCompleted(@Param("pilgrimageId") Long pilgrimageId);

    /** 심사 반려로 완주가 깨졌을 때 되돌린다. */
    int markInProgress(@Param("pilgrimageId") Long pilgrimageId);

    /** 여권 화면용 전체 슬롯. 도장이 없는 칸도 NULL 로 함께 나온다. */
    List<PassportSlotRow> findPassportSlots(@Param("userId") Long userId);

    /** 완주한 코스 수. 3코스 중간본·전체 회향 판정의 기준이다. */
    int countCompletedCourses(@Param("userId") Long userId);

    /** 지금까지 받은 완료 도장 총수(전 코스 합산). 60곳 회향 판정에 쓴다. */
    int countCompletedStamps(@Param("userId") Long userId);
}
