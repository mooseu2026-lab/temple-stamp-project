package com.templestamp.thinkbox;

import com.templestamp.thinkbox.dto.ThinkboxRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ThinkboxMapper {

    int save(Thinkbox thinkbox);

    /** 도장에서 넘어온 사본. 이미 있으면 아무것도 하지 않는다(재시도 안전). */
    int saveFromMission(Thinkbox thinkbox);

    Optional<Thinkbox> findById(@Param("thinkboxId") Long thinkboxId);

    Optional<Thinkbox> findByStampId(@Param("stampId") Long stampId);

    /** 사찰에 묶인 DIRECT 글. 그 절에 하나뿐이다. */
    Optional<Thinkbox> findDirectBySite(@Param("userId") Long userId, @Param("siteId") Long siteId);

    /** 여섯 달 전 오늘. 없으면 empty — 그것이 정상이다. */
    Optional<ThinkboxRow> findFlashback(@Param("userId") Long userId);

    List<ThinkboxRow> findByUserId(@Param("userId") Long userId,
                                   @Param("sort") String sort,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    long countByUserId(@Param("userId") Long userId);

    int update(@Param("thinkboxId") Long thinkboxId,
               @Param("body") String body,
               @Param("isPrivate") boolean isPrivate);

    /** 탈퇴 — 그 사람의 글을 전부 지운다(도장에서 만들어진 사본도 함께). */
    int deleteByUser(@Param("userId") Long userId);

    int delete(@Param("thinkboxId") Long thinkboxId);

    /**
     * 전자책 수록 대상. 글 단위 공개 설정과 EBOOK_PUBLIC 약관 동의를 모두 만족한 글만 나온다.
     * ebookType 이 PILGRIMAGE 면 그 순례의 글만, 아니면 그 사용자의 전체 글을 모은다.
     */
    List<ThinkboxRow> findPublishable(@Param("userId") Long userId,
                                      @Param("courseId") Long courseId);
}
