package com.templestamp.ebook;

import com.templestamp.ebook.dto.PrintOrderAddress;
import com.templestamp.ebook.dto.PrintOrderRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface PrintOrderMapper {

    int save(PrintOrder order);

    /** 배송정보는 본체와 따로 넣는다 — 같은 트랜잭션 안이라 둘이 어긋날 일은 없다. */
    int saveAddress(PrintOrderAddress address);

    Optional<PrintOrderAddress> findAddress(@Param("printOrderId") Long printOrderId);

    Optional<PrintOrder> findById(@Param("printOrderId") Long printOrderId);

    Optional<PrintOrderRow> findRowById(@Param("printOrderId") Long printOrderId);

    List<PrintOrderRow> findRowsByUserId(@Param("userId") Long userId);

    int existsActive(@Param("userId") Long userId, @Param("ebookId") Long ebookId);

    List<PrintOrderRow> findRowsForAdmin(@Param("status") String status,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    long countForAdmin(@Param("status") String status);

    /**
     * 상태 전이. <b>지금 상태를 조건에 걸어</b> 한 문장으로 끝낸다 —
     * 먼저 읽고 나중에 쓰면 그 사이에 다른 관리자가 같은 주문을 옮길 수 있다.
     * 영향 행 수 0 = 그 전이가 허용되지 않았다.
     */
    int transition(@Param("printOrderId") Long printOrderId,
                   @Param("next") String next,
                   @Param("allowedFrom") List<String> allowedFrom,
                   @Param("trackingNo") String trackingNo,
                   @Param("cancelReason") String cancelReason);

    /* ---------------- 탈퇴 연쇄(챕터 9 §2-5) ---------------- */

    int cancelRequestedByUser(@Param("userId") Long userId);

    /** 이미 실물이 움직이는 주문은 취소하지 않는다. 사람이 볼 표시만 켠다. */
    int flagReviewByUser(@Param("userId") Long userId);

    /** 탈퇴 정리 — 끝난(취소된) 주문은 행째 지운다. 그래야 그 전자책도 지울 수 있다. */
    int deleteCanceledByUser(@Param("userId") Long userId);

    int deleteAddressByUser(@Param("userId") Long userId);
}
