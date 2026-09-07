package com.templestamp.ebook;

import com.templestamp.ebook.dto.PrintOrderDetailResponse;
import com.templestamp.ebook.dto.PrintOrderRequest;
import com.templestamp.ebook.dto.PrintOrderResponse;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.user.AccountDeletionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 인쇄 주문(챕터 9 §4·§9-2).
 * <p>
 * 이 표의 요점은 <b>어디부터 되돌릴 수 없는가</b>다. REQUESTED 는 종이가 아직 안 움직여 취소가 되고,
 * PRINTING 부터는 인쇄소가 이미 들고 있어 시스템이 취소해도 그 일이 되돌아오지 않는다.
 * 그래서 전이를 코드가 아니라 <b>UPDATE 의 WHERE 절</b>에 넣는다 — 먼저 읽고 나중에 쓰면
 * 두 관리자가 같은 주문을 동시에 옮길 때 둘 다 성공한다.
 */
@SpringBootTest
class PrintOrderServiceTest {

    private static final long COURSE_ID = 1L;

    @Autowired PrintOrderService printOrderService;
    @Autowired PrintOrderMapper printOrderMapper;
    @Autowired AccountDeletionService accountDeletionService;
    @Autowired JdbcTemplate jdbc;

    private long userId;
    private long otherId;
    private long readyEbookId;
    private String readyKey;

    @BeforeEach
    void setUp() {
        userId = newUser("print-test@test.com", "인쇄시험");
        otherId = newUser("print-other@test.com", "이웃");
        // 키를 테스트마다 다르게 준다. 같은 문자열을 쓰면 앞 테스트가 큐에 넣은 키까지 함께 세어져
        // "몇 건이 큐에 갔는가" 가 실행 순서에 따라 흔들린다 — 실제로 그렇게 한 번 깨졌다.
        readyKey = "EBOOK/%d/ready.pdf".formatted(userId);
        readyEbookId = ebook(userId, Ebook.READY, readyKey);
    }

    @AfterEach
    void tearDown() {
        for (long u : new long[]{userId, otherId}) {
            jdbc.update("DELETE FROM print_order_address WHERE print_order_id IN "
                    + "(SELECT print_order_id FROM print_order WHERE user_id = ?)", u);
            jdbc.update("DELETE FROM print_order WHERE user_id = ?", u);
            jdbc.update("DELETE FROM ebook WHERE user_id = ?", u);
            jdbc.update("DELETE FROM storage_orphan WHERE file_key LIKE ?", "EBOOK/" + u + "/%");
            jdbc.update("DELETE FROM user_agreement WHERE user_id = ?", u);
            jdbc.update("DELETE FROM refresh_token WHERE user_id = ?", u);
            jdbc.update("DELETE FROM users WHERE user_id = ?", u);
        }
    }

    /* ---------------- 자격 ---------------- */

    @Test
    @DisplayName("① 만들어진 책만 인쇄한다 — 아니면 400 PRINT-4001")
    void onlyReadyBooks() {
        long pending = ebook(userId, Ebook.REQUESTED, null);
        assertThatThrownBy(() -> printOrderService.order(userId, request(pending)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PRINT_4001);
    }

    @Test
    @DisplayName("② 남의 책은 주문할 수 없다")
    void notMyBook() {
        assertThatThrownBy(() -> printOrderService.order(otherId, request(readyEbookId)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.EBOOK_4030);
    }

    @Test
    @DisplayName("③ 같은 책을 두 번 주문할 수 없다 — 취소한 것은 다시 된다")
    void noDoubleOrder() {
        PrintOrderResponse first = printOrderService.order(userId, request(readyEbookId));
        // 중복 신청은 PRINT-4091, 표 밖 전이는 PRINT-4090 이다. 둘 다 409 지만 사용자가 할 일이 다르다 —
        // 하나는 다른 책을 고르는 것이고 하나는 기다리는 것이다.
        assertThatThrownBy(() -> printOrderService.order(userId, request(readyEbookId)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PRINT_4091);

        printOrderService.cancel(userId, first.printOrderId());
        assertThat(printOrderService.order(userId, request(readyEbookId))).isNotNull();
    }

    /* ---------------- 배송정보 분리 ---------------- */

    @Test
    @DisplayName("④ 목록은 주소를 가져오지도 않는다 — 가리는 것이 아니라 없다")
    void listNeverCarriesAddress() {
        printOrderService.order(userId, request(readyEbookId));
        List<PrintOrderResponse> list = printOrderService.getMine(userId);
        assertThat(list).hasSize(1);

        var names = java.util.Arrays.stream(PrintOrderResponse.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList();
        assertThat(names).doesNotContain("address", "recipientName", "recipientPhone", "shipping");
    }

    @Test
    @DisplayName("⑤ 단건 조회에는 배송정보가 실린다 — 본인 화면이라 가리지 않는다")
    void detailCarriesAddress() {
        PrintOrderResponse created = printOrderService.order(userId, request(readyEbookId));
        PrintOrderDetailResponse detail = printOrderService.get(userId, created.printOrderId());

        assertThat(detail.shipping()).isNotNull();
        assertThat(detail.shipping().recipient()).isEqualTo("수령인");
        assertThat(detail.shipping().phone()).as("하이픈은 지우고 저장한다").isEqualTo("01012345678");
        assertThat(detail.shipping().address()).contains("우정국로").contains("조계사");
    }

    @Test
    @DisplayName("⑥ 남의 주문은 '없다' 고 답한다")
    void othersOrderLooksMissing() {
        PrintOrderResponse created = printOrderService.order(userId, request(readyEbookId));
        assertThatThrownBy(() -> printOrderService.get(otherId, created.printOrderId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PRINT_4040);
    }

    /* ---------------- 챕터 10 — cancelable ---------------- */

    @Test
    @DisplayName("⑫ cancelable 은 서버가 계산한다 — 목록·단건 둘 다, REQUESTED 에서만 true")
    void cancelableIsServerComputed() {
        PrintOrderResponse created = printOrderService.order(userId, request(readyEbookId));
        assertThat(created.cancelable()).as("막 신청한 주문은 취소할 수 있다").isTrue();

        assertThat(printOrderService.getMine(userId))
                .singleElement()
                .satisfies(r -> assertThat(r.cancelable()).isTrue());
        assertThat(printOrderService.get(userId, created.printOrderId()).cancelable()).isTrue();

        // 관리자가 확인하면 인쇄가 걸린다. 그 뒤로는 사용자가 취소하지 못한다.
        printOrderService.updateStatus(created.printOrderId(), PrintOrder.CONFIRMED, null, null);

        assertThat(printOrderService.getMine(userId))
                .singleElement()
                .satisfies(r -> assertThat(r.cancelable()).isFalse());
        assertThat(printOrderService.get(userId, created.printOrderId()).cancelable()).isFalse();
    }

    @Test
    @DisplayName("⑬ 관리자 응답에는 cancelable 이 없다 — 관리자의 취소는 다른 규칙이다")
    void adminResponseHasNoCancelable() {
        var names = java.util.Arrays.stream(
                        com.templestamp.admin.dto.AdminPrintOrderResponse.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList();
        assertThat(names).as("사용자 규칙을 관리자 화면이 따라 쓰면 CONFIRMED 취소가 막힌다")
                .doesNotContain("cancelable");
    }

    /* ---------------- 상태 기계 ---------------- */

    @Test
    @DisplayName("⑦ 표대로 가는 길 — REQUESTED → CONFIRMED → PRINTING → SHIPPED → DONE")
    void happyPath() {
        long id = printOrderService.order(userId, request(readyEbookId)).printOrderId();
        printOrderService.updateStatus(id, PrintOrder.CONFIRMED, null, null);
        printOrderService.updateStatus(id, PrintOrder.PRINTING, null, null);
        printOrderService.updateStatus(id, PrintOrder.SHIPPED, "1234567890", null);
        printOrderService.updateStatus(id, PrintOrder.DONE, null, null);
        assertThat(status(id)).isEqualTo(PrintOrder.DONE);
    }

    @Test
    @DisplayName("⑧ 표 밖의 전이는 전부 같은 답 409 PRINT-4090")
    void offTableTransitionsAllSame() {
        long id = printOrderService.order(userId, request(readyEbookId)).printOrderId();

        deny(() -> printOrderService.updateStatus(id, PrintOrder.PRINTING, null, null));   // 확인을 건너뛴다
        deny(() -> printOrderService.updateStatus(id, PrintOrder.SHIPPED, "1", null));
        deny(() -> printOrderService.updateStatus(id, PrintOrder.DONE, null, null));

        printOrderService.updateStatus(id, PrintOrder.CONFIRMED, null, null);
        deny(() -> printOrderService.updateStatus(id, PrintOrder.CONFIRMED, null, null));  // 두 번 확인
        deny(() -> printOrderService.updateStatus(id, PrintOrder.SHIPPED, "1", null));     // 인쇄중을 건너뛴다

        printOrderService.updateStatus(id, PrintOrder.PRINTING, null, null);
        deny(() -> printOrderService.updateStatus(id, PrintOrder.CANCELED, null, "사유"));  // 종이가 나갔다
    }

    @Test
    @DisplayName("⑨ 사용자 취소는 REQUESTED 에서만 — 확인 뒤에는 409")
    void userCancelOnlyBeforeConfirm() {
        long id = printOrderService.order(userId, request(readyEbookId)).printOrderId();
        printOrderService.cancel(userId, id);
        assertThat(status(id)).isEqualTo(PrintOrder.CANCELED);
        assertThat(printOrderMapper.findById(id)).as("행은 남는다").isPresent();

        long second = printOrderService.order(userId, request(readyEbookId)).printOrderId();
        printOrderService.updateStatus(second, PrintOrder.CONFIRMED, null, null);
        assertThatThrownBy(() -> printOrderService.cancel(userId, second))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PRINT_4090);
    }

    @Test
    @DisplayName("⑩ 발송에는 송장이, 취소에는 사유가 있어야 한다")
    void requiredFields() {
        long id = printOrderService.order(userId, request(readyEbookId)).printOrderId();
        printOrderService.updateStatus(id, PrintOrder.CONFIRMED, null, null);
        printOrderService.updateStatus(id, PrintOrder.PRINTING, null, null);

        assertThatThrownBy(() -> printOrderService.updateStatus(id, PrintOrder.SHIPPED, "  ", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.COMMON_4000);

        long other = printOrderService.order(userId, request(ebook(userId, Ebook.READY, "EBOOK/%d/b.pdf".formatted(userId))))
                .printOrderId();
        assertThatThrownBy(() -> printOrderService.updateStatus(other, PrintOrder.CANCELED, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.COMMON_4000);
    }

    /* ---------------- 탈퇴 연쇄 ---------------- */

    @Test
    @DisplayName("⑪ 탈퇴 — 확인 전 주문은 사라지고 진행 중인 주문은 표시만 켠다")
    void withdrawalSplitsByStatus() {
        long willVanish = printOrderService.order(userId, request(readyEbookId)).printOrderId();

        long inFlightBook = ebook(userId, Ebook.READY, "EBOOK/%d/inflight.pdf".formatted(userId));
        long inFlight = printOrderService.order(userId, request(inFlightBook)).printOrderId();
        printOrderService.updateStatus(inFlight, PrintOrder.CONFIRMED, null, null);
        printOrderService.updateStatus(inFlight, PrintOrder.PRINTING, null, null);

        accountDeletionService.deleteCascade(userId);

        assertThat(printOrderMapper.findById(willVanish))
                .as("확인 전 주문은 취소된 뒤 행까지 지운다 — 그래야 그 책도 지울 수 있다").isEmpty();
        var kept = printOrderMapper.findById(inFlight).orElseThrow();
        assertThat(kept.getStatus()).as("종이가 이미 나갔다 — 취소하지 않는다").isEqualTo(PrintOrder.PRINTING);
        assertThat(kept.getNeedsReview()).as("사람이 봐야 한다").isTrue();

        assertThat(printOrderMapper.findAddress(inFlight))
                .as("배송정보는 지운다 — 개인정보이고 관리자가 이미 받아 적었다").isEmpty();

        Integer books = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ebook WHERE user_id = ?", Integer.class, userId);
        assertThat(books).as("주문이 붙든 책 한 권만 남는다").isEqualTo(1);
        Integer queued = jdbc.queryForObject(
                "SELECT COUNT(*) FROM storage_orphan WHERE file_key = ?", Integer.class, readyKey);
        assertThat(queued).as("지운 책의 파일 키는 큐로 간다").isEqualTo(1);
    }

    /* ---------------- 도구 ---------------- */

    private void deny(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PRINT_4090);
    }

    private PrintOrderRequest request(long ebookId) {
        return new PrintOrderRequest(ebookId, 2, "수령인", "010-1234-5678",
                "03145", "서울특별시 종로구 우정국로 55", "조계사", "점검용");
    }

    private String status(long id) {
        return jdbc.queryForObject("SELECT status FROM print_order WHERE print_order_id = ?", String.class, id);
    }

    private long newUser(String email, String nickname) {
        jdbc.update("INSERT INTO users (email, password, nickname, role, tier, locale) "
                + "VALUES (?, '$2a$10$abcdefghijklmnopqrstuv', ?, 'USER', 'AGE40', 'ko')", email, nickname);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    /** 스냅샷 해시 자리는 64자다. 두 UUID 를 이어 붙여 채운다 — 값 자체는 의미가 없고 유일하기만 하면 된다. */
    private String hash32() {
        String raw = java.util.UUID.randomUUID().toString().replace("-", "")
                + java.util.UUID.randomUUID().toString().replace("-", "");
        return raw.substring(0, 64);
    }

    private long ebook(long owner, String status, String key) {
        jdbc.update("INSERT INTO ebook (user_id, ebook_type, snapshot_hash, status, pdf_key, "
                        + "page_count, byte_size, built_at) VALUES (?, 'PERSONAL', ?, ?, ?, 10, 1000, NOW())",
                owner, hash32(), status, key);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
