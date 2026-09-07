package com.templestamp.site;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface SiteMapper {

    /**
     * CSV 반입이 쓰는 사찰 찾기(챕터 8 §4). <b>이름만으로 찾지 않는다</b> —
     * 이 프로젝트만 해도 같은 이름의 절이 7이름 15곳이라, 이름만 맞추면 다른 고장의 절에 원고가 붙는다.
     * 시군구는 seed_key(권역:이름:구분)의 마지막 조각과 견준다(정리.md §6-9).
     */
    List<Long> findIdsByNameAndSigungu(@Param("name") String name, @Param("sigungu") String sigungu);

    Optional<Site> findById(@Param("siteId") Long siteId);

    /** 인증에 쓰는 조회. 협의가 끝난(ACTIVE) 사찰만 순례 대상이다. */
    Optional<Site> findActiveById(@Param("siteId") Long siteId);

    List<Site> search(@Param("keyword") String keyword,
                      @Param("offset") int offset,
                      @Param("limit") int limit);

    long countSearch(@Param("keyword") String keyword);

    int save(Site site);

    int update(Site site);

    /** QR 유출 시 버전을 올려 기존 QR 을 일괄 무효화한다. */
    int bumpQrVersion(@Param("siteId") Long siteId);

    /** 상태만 바꾼다. 좌표·이름은 건드리지 않는다 — 등록(PUT)과 상태 전이(PATCH)를 분리했기 때문. */
    int updateStatus(@Param("siteId") Long siteId, @Param("status") String status);

    /** 이 사찰이 ACTIVE 코스에 배정된 수. 0 이 아니면 INACTIVE 로 내릴 수 없다. */
    int countActiveCourseAssignments(@Param("siteId") Long siteId);

    /**
     * 관리자 목록. {@code status} 가 null 이면 전부(DRAFT 포함) — 등록 직후의 사찰을 찾아야 하므로.
     * 값을 주면 그 상태만 본다.
     */
    List<Site> searchAnyStatus(@Param("keyword") String keyword,
                               @Param("status") String status,
                               @Param("offset") int offset,
                               @Param("limit") int limit);

    long countSearchAnyStatus(@Param("keyword") String keyword, @Param("status") String status);

    /* v4 시드 — 재실행 멱등. seed_key 는 REGION:이름:구분 */
    Long findIdBySeedKey(@Param("seedKey") String seedKey);
    int updateSeedKey(@Param("siteId") Long siteId, @Param("seedKey") String seedKey);
}
