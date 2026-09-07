package com.templestamp.ebook;

import com.templestamp.ebook.dto.EbookRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface EbookMapper {

    /**
     * 대기열 등록. (순례, 종류) 에 UNIQUE 가 걸려 있어 완주 처리가 재시도돼도 두 번 쌓이지 않는다.
     * 영향 행 수가 0 이면 이미 있었다는 뜻이다.
     */
    int enqueue(Ebook ebook);

    Optional<Ebook> findById(@Param("ebookId") Long ebookId);

    /**
     * 순례에 매이지 않는 전자책(중간본·회향본)이 이미 있는가.
     * QUEUED·BUILDING·READY 를 "있다" 로 본다 — FAILED 만 다시 큐에 넣을 수 있다.
     */
    /** 같은 마일스톤의 전자일기장이 이미 있는가. 종류만 보면 6코스 책이 3코스 책과 같은 것이 된다. */
    Optional<Ebook> findAliveByUserTypeAndMilestone(@Param("userId") Long userId,
                                                    @Param("ebookType") String ebookType,
                                                    @Param("milestone") Integer milestone);

    Optional<Ebook> findAliveByUserAndType(@Param("userId") Long userId,
                                           @Param("ebookType") String ebookType);

    Optional<Ebook> findByPilgrimageAndType(@Param("pilgrimageId") Long pilgrimageId,
                                            @Param("ebookType") String ebookType);

    List<EbookRow> findRowsByUserId(@Param("userId") Long userId);

    /** 조판 결과 등록. 키가 들어오면 READY 로 옮긴다. */
    int updateFiles(@Param("ebookId") Long ebookId,
                    @Param("pdfKey") String pdfKey,
                    @Param("epubKey") String epubKey);

    /* ---------------- 챕터 9 — 개인 소장본 ---------------- */

    /** 같은 재료로 만든 책이 이미 있는가. (user_id, snapshot_hash) 유니크가 마지막 방어선이다. */
    Optional<Ebook> findByUserAndHash(@Param("userId") Long userId,
                                      @Param("snapshotHash") String snapshotHash);

    /** 만드는 중인 책 수. 사용자당 1건이 상한이다 — 줄줄이 걸어 두면 청소기가 한 사람 것만 돈다. */
    int countRequested(@Param("userId") Long userId);

    /** 오늘 완성된 권수. 하루 상한은 저장소 비용을 지키는 선이다. */
    int countReadyToday(@Param("userId") Long userId);

    int insertPersonal(Ebook ebook);

    /**
     * 만들기 직전에 <b>있는 행</b>을 잠근다(챕터 8 §4 규칙).
     * 없는 범위를 잠그면 갭 잠금끼리 양립해 상호배제가 되지 않는다.
     */
    Optional<Ebook> lockById(@Param("ebookId") Long ebookId);

    int markReady(@Param("ebookId") Long ebookId,
                  @Param("pdfKey") String pdfKey,
                  @Param("pageCount") int pageCount,
                  @Param("byteSize") long byteSize);

    int markFailed(@Param("ebookId") Long ebookId, @Param("failReason") String failReason);

    /** 청소기가 집을 대상. 오래 기다린 것부터. */
    List<Long> findRequestedIds(@Param("limit") int limit);

    /**
     * 상한을 넘겨 지울 후보. 최신 {@code keep} 권을 뺀 나머지를 오래된 것부터 준다.
     * <b>인쇄 주문이 걸린 책은 빠진다</b> — 종이로 찍는 중인 책을 지우면 되돌릴 길이 없다(함정 7).
     */
    List<Ebook> findTrimmable(@Param("userId") Long userId, @Param("keep") int keep);

    int deleteById(@Param("ebookId") Long ebookId);

    /** 탈퇴 연쇄가 쓴다 — 지우기 전에 파일 키를 큐로 옮겨야 한다. */
    List<Ebook> findAllByUser(@Param("userId") Long userId);

    int deleteByUser(@Param("userId") Long userId);

    Optional<com.templestamp.ebook.dto.EbookRow> findRowById(@Param("ebookId") Long ebookId);
}
