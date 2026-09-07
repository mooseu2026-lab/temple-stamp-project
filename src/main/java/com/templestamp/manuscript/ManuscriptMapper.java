package com.templestamp.manuscript;

import com.templestamp.manuscript.dto.ManuscriptRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ManuscriptMapper {

    int insert(Manuscript manuscript);

    Optional<Manuscript> findById(@Param("manuscriptId") Long manuscriptId);

    /**
     * 변형 번호를 매기기 전에 그 키를 잠근다. 행이 하나도 없어도 <b>범위(gap)</b> 가 잠기므로,
     * 같은 키로 동시에 들어온 두 등록이 한 줄로 선다(챕터 8 함정 2).
     * 마지막 방어선은 유니크 제약이다 — 잠금이 뚫려도 두 번째는 거기서 막힌다.
     */
    Long lockKeyOwner(@Param("siteId") Long siteId,
                      @Param("verseNo") Integer verseNo,
                      @Param("kind") String kind);

    /** 상한 검사용. 퇴역한 것은 세지 않는다. */
    int countActive(@Param("siteKey") Long siteKey,
                    @Param("verseNo") Integer verseNo,
                    @Param("kind") String kind);

    /** 번호는 재사용하지 않는다 — 퇴역한 것까지 포함한 최대값 + 1. */
    int maxVariantNo(@Param("siteKey") Long siteKey,
                     @Param("verseNo") Integer verseNo,
                     @Param("kind") String kind);

    /** 본문이 완전히 같은 원고가 이미 있는가(중복 차단). */
    int countSameBody(@Param("siteKey") Long siteKey,
                      @Param("verseNo") Integer verseNo,
                      @Param("kind") String kind,
                      @Param("body") String body);

    /** 수정 — 제목·본문만. 반려본을 고치면 자동으로 DRAFT 로 돌아간다(사유는 이력으로 남긴다). */
    int updateContent(@Param("manuscriptId") Long manuscriptId,
                      @Param("title") String title,
                      @Param("body") String body);

    int markSubmitted(@Param("manuscriptId") Long manuscriptId);

    int markApproved(@Param("manuscriptId") Long manuscriptId, @Param("reviewerId") Long reviewerId);

    int markRejected(@Param("manuscriptId") Long manuscriptId,
                     @Param("reviewerId") Long reviewerId,
                     @Param("reason") String reason);

    int markRetired(@Param("manuscriptId") Long manuscriptId);

    /** 기본 원고를 승인하면 그 구·종류의 이전 기본 원고는 물러난다(기본은 한 편뿐이다). */
    int retirePreviousDefault(@Param("verseNo") Integer verseNo,
                              @Param("kind") String kind,
                              @Param("exceptId") Long exceptId);

    /** 선택 후보 — 승인된 것만, 변형 번호 순. 순서가 고정돼야 같은 사용자가 늘 같은 글을 본다. */
    List<Manuscript> findApproved(@Param("siteKey") Long siteKey,
                                  @Param("verseNo") Integer verseNo,
                                  @Param("kind") String kind);

    List<ManuscriptRow> findRows(@Param("authorId") Long authorId,
                                 @Param("siteId") Long siteId,
                                 @Param("verseNo") Integer verseNo,
                                 @Param("kind") String kind,
                                 @Param("status") String status,
                                 @Param("offset") int offset,
                                 @Param("size") int size);

    long countRows(@Param("authorId") Long authorId,
                   @Param("siteId") Long siteId,
                   @Param("verseNo") Integer verseNo,
                   @Param("kind") String kind,
                   @Param("status") String status);
}
