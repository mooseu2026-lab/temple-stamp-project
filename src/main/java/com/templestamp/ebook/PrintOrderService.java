package com.templestamp.ebook;

import com.templestamp.ebook.dto.PrintOrderAddress;
import com.templestamp.ebook.dto.PrintOrderDetailResponse;
import com.templestamp.ebook.dto.PrintOrderRequest;
import com.templestamp.ebook.dto.PrintOrderResponse;
import com.templestamp.ebook.dto.PrintOrderRow;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 인쇄 주문(챕터 9 §4).
 * <pre>
 *   REQUESTED ─확인─▶ CONFIRMED ─인쇄중─▶ PRINTING ─발송─▶ SHIPPED ─완료─▶ DONE
 *       │                  │
 *       │                  └─관리자 취소(사유 필수)─▶ CANCELED
 *       └─사용자 취소(DELETE)─▶ CANCELED
 * </pre>
 * <b>PRINTING 부터는 취소가 없다.</b> 종이가 이미 나가고 있어 되돌릴 수 없기 때문이다.
 * 그래서 전이를 코드로 판단하지 않고 <b>UPDATE 의 WHERE 절</b>에 넣는다 — 먼저 읽고 나중에 쓰면
 * 그 사이에 다른 관리자가 같은 주문을 옮길 수 있고, 그때는 두 사람 다 성공한다.
 * <p>
 * 배송정보는 {@code print_order_address} 에 따로 둔다. 목록 질의가 그 표를 읽지 않으므로
 * 목록 응답으로 주소가 새어 나갈 길이 <b>애초에 없다</b>(보상 배송정보와 같은 원칙).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrintOrderService {

    private final PrintOrderMapper printOrderMapper;
    private final EbookService ebookService;

    @Transactional
    public PrintOrderResponse order(Long userId, PrintOrderRequest request) {
        Ebook ebook = verifyEligibility(userId, request.ebookId());

        PrintOrder order = PrintOrder.builder()
                .userId(userId)
                .ebookId(ebook.getEbookId())
                .quantity(request.quantity())
                .note(request.note())
                .build();
        printOrderMapper.save(order);

        printOrderMapper.saveAddress(PrintOrderAddress.builder()
                .printOrderId(order.getPrintOrderId())
                .recipient(request.recipientName().trim())
                .phone(request.recipientPhone().replace("-", ""))
                .postalCode(request.postalCode())
                .address(joinAddress(request))
                .memo(request.note())
                .build());

        log.info("인쇄 주문. userId={}, ebookId={}, orderId={}",
                userId, ebook.getEbookId(), order.getPrintOrderId());
        return PrintOrderResponse.from(rowOf(order.getPrintOrderId()));
    }

    /**
     * 자격 검증. 정본 §4-1 이 "본인 <b>READY</b> 전자책만" 으로 바꿨다 —
     * 옛 규칙(회향본만)은 개인 소장본이 인쇄 대상이 되면서 뜻을 잃었다.
     * 실패 사유는 각각 다른 코드로 돌려준다. "안 됩니다" 하나로 묶으면 사용자가 무엇을 고쳐야 할지 모른다.
     */
    private Ebook verifyEligibility(Long userId, Long ebookId) {
        Ebook ebook = ebookService.getOwned(userId, ebookId);   // 남의 것이면 EBOOK-4030
        if (!ebook.isReady()) {
            throw new BusinessException(ErrorCode.PRINT_4001,
                    "아직 만들어지지 않은 전자책입니다. 완성된 뒤에 신청해 주세요.");
        }
        if (printOrderMapper.existsActive(userId, ebookId) == 1) {
            // 중복 신청과 "표 밖 전이" 는 다른 일이라 코드를 나눈다. 둘 다 409 지만
            // 사용자가 할 일이 다르다 — 하나는 기다리는 것이고 하나는 다른 책을 고르는 것이다.
            throw new BusinessException(ErrorCode.PRINT_4091, "이미 이 전자책으로 신청한 주문이 있습니다.");
        }
        return ebook;
    }

    public List<PrintOrderResponse> getMine(Long userId) {
        return printOrderMapper.findRowsByUserId(userId).stream()
                .map(PrintOrderResponse::from)
                .toList();
    }

    /** 1건 조회 — 본인만, 배송정보 포함. 남의 것은 404 다(있다는 사실 자체가 정보다). */
    public PrintOrderDetailResponse get(Long userId, Long printOrderId) {
        PrintOrderRow row = printOrderMapper.findRowById(printOrderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRINT_4040));
        if (!row.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PRINT_4040);
        }
        return PrintOrderDetailResponse.of(row, printOrderMapper.findAddress(printOrderId).orElse(null));
    }

    /**
     * 사용자 취소. REQUESTED 에서만. <b>행은 남는다</b> — 지우면 "내가 신청했다가 취소했다" 가
     * 사용자 화면에서 사라지고, 관리자도 무슨 일이 있었는지 알 길이 없다.
     */
    @Transactional
    public void cancel(Long userId, Long printOrderId) {
        PrintOrder order = printOrderMapper.findById(printOrderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRINT_4040));
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PRINT_4040);
        }
        int moved = printOrderMapper.transition(printOrderId, PrintOrder.CANCELED,
                List.of(PrintOrder.REQUESTED), null, "사용자 취소");
        if (moved == 0) {
            throw new BusinessException(ErrorCode.PRINT_4090,
                    "이미 처리가 시작되어 취소할 수 없습니다.");
        }
    }

    /* ---------------- 관리자 ---------------- */

    /** 관리자 목록에는 배송정보가 실린다 — 실제로 보내야 하는 사람이다(보상 심사 목록과 같다). */
    public PageResponse<PrintOrderRow> getAllForAdmin(String status, int page, int size) {
        long total = printOrderMapper.countForAdmin(status);
        List<PrintOrderRow> rows = printOrderMapper.findRowsForAdmin(status, page * size, size);
        return PageResponse.of(rows, page, size, total);
    }

    /** 관리자 목록이 주소를 붙일 때 쓴다 — 본체 질의는 주소를 읽지 않으므로 한 번 더 읽어야 한다. */
    public PrintOrderAddress addressOf(Long printOrderId) {
        return printOrderMapper.findAddress(printOrderId).orElse(null);
    }

    public PrintOrderDetailResponse getForAdmin(Long printOrderId) {
        PrintOrderRow row = printOrderMapper.findRowById(printOrderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRINT_4040));
        return PrintOrderDetailResponse.of(row, printOrderMapper.findAddress(printOrderId).orElse(null));
    }

    /**
     * 관리자 전이. 표 밖의 전이는 전부 409 {@code PRINT-4090} 이다 —
     * 어느 전이가 왜 막혔는지를 코드로 갈라 주면 밖에서 내부 상태를 되짚을 수 있다.
     */
    @Transactional
    public void updateStatus(Long printOrderId, String status, String trackingNo, String reason) {
        printOrderMapper.findById(printOrderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRINT_4040));

        List<String> from = switch (status) {
            case PrintOrder.CONFIRMED -> List.of(PrintOrder.REQUESTED);
            case PrintOrder.PRINTING -> List.of(PrintOrder.CONFIRMED);
            case PrintOrder.SHIPPED -> List.of(PrintOrder.PRINTING);
            case PrintOrder.DONE -> List.of(PrintOrder.SHIPPED);
            // 취소는 확인까지. PRINTING 부터는 종이가 이미 나가고 있다.
            case PrintOrder.CANCELED -> List.of(PrintOrder.REQUESTED, PrintOrder.CONFIRMED);
            default -> throw new BusinessException(ErrorCode.COMMON_4000, "알 수 없는 주문 상태입니다.");
        };

        if (PrintOrder.SHIPPED.equals(status) && (trackingNo == null || trackingNo.isBlank())) {
            throw new BusinessException(ErrorCode.COMMON_4000, "송장번호를 입력해 주세요.");
        }
        if (PrintOrder.CANCELED.equals(status) && (reason == null || reason.isBlank())) {
            throw new BusinessException(ErrorCode.COMMON_4000, "취소 사유를 입력해 주세요.");
        }

        int moved = printOrderMapper.transition(printOrderId, status, from, trackingNo, reason);
        if (moved == 0) {
            throw new BusinessException(ErrorCode.PRINT_4090, "현재 상태에서는 옮길 수 없습니다.");
        }
        log.info("인쇄 주문 전이. orderId={}, → {}", printOrderId, status);
    }

    /* ---------------- 탈퇴 연쇄 (챕터 9 §2-5) ---------------- */

    /**
     * 아직 확인 전이면 취소하고, 이미 움직였으면 <b>사람이 볼 표시만</b> 켠다.
     * 배송정보는 지운다 — 개인정보이고, 남은 주문은 관리자가 이미 받아 적었다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int handleWithdrawal(Long userId) {
        int canceled = printOrderMapper.cancelRequestedByUser(userId);
        int flagged = printOrderMapper.flagReviewByUser(userId);
        printOrderMapper.deleteAddressByUser(userId);
        // 끝난 주문은 행째 지운다. 남겨 두면 그 주문이 붙든 전자책을 지울 수 없어
        // 탈퇴한 사람의 PDF 가 저장소에 그대로 남는다 — 실측에서 FK 로 막혀 드러났다.
        printOrderMapper.deleteCanceledByUser(userId);
        if (flagged > 0) {
            log.warn("탈퇴했지만 실물이 진행 중인 인쇄 주문 {}건 — 사람이 봐야 한다. userId={}", flagged, userId);
        }
        return canceled + flagged;
    }

    /* ---------------- 내부 ---------------- */

    private PrintOrderRow rowOf(Long printOrderId) {
        return printOrderMapper.findRowById(printOrderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRINT_4040));
    }

    /** 요청 본문의 주소 두 칸을 한 줄로 합친다. 표는 한 칸이라 여기서 정리한다. */
    private String joinAddress(PrintOrderRequest request) {
        String detail = request.addressDetail();
        String joined = detail == null || detail.isBlank()
                ? request.address().trim()
                : request.address().trim() + " " + detail.trim();
        return joined.length() > 200 ? joined.substring(0, 200) : joined;
    }
}
