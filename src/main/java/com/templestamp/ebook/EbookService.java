package com.templestamp.ebook;

import com.templestamp.ebook.dto.DownloadUrlResponse;
import com.templestamp.ebook.dto.EbookResponse;
import com.templestamp.ebook.dto.EbookRow;
import com.templestamp.global.config.EbookProperties;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.upload.ObjectStorageClient;
import com.templestamp.user.StorageOrphanMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * 전자책. 챕터 9 에서 <b>개인 소장본</b>이 되었다 — 완주와 무관하고, 비공개 생각상자까지 들어간다.
 * <p>
 * 이 서비스의 축은 <b>스냅샷 해시</b> 하나다. 재료(도장 id·사진 키·원고 id·생각상자 id·인증서 번호)를
 * 정렬해 SHA-256 을 낸 값이 같으면 같은 책이고, 그러면 새로 만들지 않고 있던 것을 준다.
 * 이 규칙이 없으면 사용자가 버튼을 누를 때마다 같은 책이 쌓인다(함정 4).
 * <p>
 * 만드는 일은 요청 트랜잭션에서 하지 않는다. PDF 조판은 초 단위로 걸리는 일이라
 * 요청을 붙들고 있으면 그동안 웹 스레드가 묶인다. 그래서 REQUESTED 행만 세워 두고
 * 청소기(5분)가 집어 간다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EbookService {

    /** 다운로드 링크가 사는 시간(§2-4). 링크를 퍼 날라도 10분 뒤에는 열리지 않는다. */
    private static final long DOWNLOAD_SECONDS = 600;
    /** 전자일기장 한 권이 나오는 간격. CompletionService 의 같은 값과 짝이다(챕터 11 결정 A). */
    private static final int INTERIM_COURSE_COUNT = 3;
    private static final String PDF = "PDF";
    private static final String EPUB = "EPUB";
    /** 전자책이 삭제될 때 파일 키가 큐로 갈 때의 사유. */
    public static final String REASON_TRIMMED = "EBOOK_TRIMMED";
    public static final String REASON_WITHDRAWN = "USER_WITHDRAWN";

    private final EbookMapper ebookMapper;
    private final EbookMaterialMapper materialMapper;
    private final StorageOrphanMapper storageOrphanMapper;
    private final ObjectStorageClient storageClient;
    private final PdfBuilder pdfBuilder;
    private final EbookProperties ebookProperties;

    /* ---------------- 요청 ---------------- */

    /** 종류를 적지 않은 옛 호출. 개인 소장본이다. */
    @Transactional
    public EbookResponse request(Long userId) {
        return request(userId, Ebook.PERSONAL);
    }

    /**
     * 만들기 요청. 같은 재료면 새로 만들지 않는다.
     * <p>
     * 종류는 둘이다. <b>PERSONAL</b> 은 스냅샷 해시로 묶이고, <b>INTERIM</b>(전자일기장)은
     * {@code (사용자, 종류, 마일스톤)} 으로 묶인다 — 3·6·9·12 마다 다른 책이기 때문이다.
     * INTERIM 을 달라고 했는데 아직 3코스에 못 미치면 거절하지 않고 개인 소장본으로 내린다.
     * 사용자가 원한 것은 "지금까지의 내 기록" 이지 이름이 아니다(챕터 11 항목 6).
     * <p>
     * 재료가 <b>하나도</b> 없을 때만 400 이다. 도장 수로 재던 옛 기준은 사진과 생각상자만 있는
     * 사람을 막았다 — 전자일기장은 도장을 요구하지 않는다(결정 B).
     *
     * @return 이미 완성된 같은 책이면 그 행(컨트롤러가 200), 아니면 대기 행(202)
     */
    @Transactional
    public EbookResponse request(Long userId, String requestedType) {
        EbookMaterials m = materialsOf(userId);
        if (m.isEmpty()) {
            throw new BusinessException(ErrorCode.EBOOK_4001);
        }

        Integer milestone = interimMilestone(userId, requestedType);
        if (milestone != null) {
            var alive = ebookMapper.findAliveByUserTypeAndMilestone(userId, Ebook.INTERIM, milestone);
            if (alive.isPresent()) {
                return toResponse(alive.get());   // 이 마일스톤의 책은 이미 있다
            }
        }

        String hash = m.snapshotHash();
        if (milestone == null) {
            Ebook same = ebookMapper.findByUserAndHash(userId, hash).orElse(null);
            if (same != null && (same.isReady() || same.isRequested())) {
                // FAILED 는 "없는 것" 으로 본다 — 실패한 책을 영원히 돌려주면 다시 시도할 길이 없다.
                return toResponse(same);
            }
        }

        if (ebookMapper.countRequested(userId) >= 1) {
            throw new BusinessException(ErrorCode.EBOOK_4092);
        }
        if (ebookMapper.countReadyToday(userId) >= ebookProperties.dailyLimit()) {
            throw new BusinessException(ErrorCode.EBOOK_4290,
                    "하루에 %d권까지 만들 수 있습니다.".formatted(ebookProperties.dailyLimit()));
        }

        if (milestone != null) {
            // 전자일기장은 해시로 묶지 않는다. 스냅샷 유니크까지 걸면 같은 재료의 개인 소장본과
            // 부딪혀, 3코스 책이 있다는 이유로 개인 소장본을 만들 수 없게 된다.
            Ebook diary = enqueue(userId, null, Ebook.INTERIM, milestone);
            log.info("전자일기장 요청. userId={}, ebookId={}, milestone={}",
                    userId, diary.getEbookId(), milestone);
            return toResponse(diary);
        }

        Ebook row = Ebook.builder()
                .userId(userId)
                .ebookType(Ebook.PERSONAL)
                .snapshotHash(hash)
                .status(Ebook.REQUESTED)
                .build();
        try {
            ebookMapper.insertPersonal(row);
        } catch (DuplicateKeyException e) {
            // 같은 순간에 두 요청이 들어왔다. 유니크가 막았으니 있던 행을 돌려준다.
            return toResponse(ebookMapper.findByUserAndHash(userId, hash)
                    .orElseThrow(() -> new BusinessException(ErrorCode.EBOOK_4092)));
        }
        log.info("전자책 요청. userId={}, ebookId={}, hash={}", userId, row.getEbookId(), hash.substring(0, 8));
        return toResponse(row);
    }

    /**
     * 이 요청이 전자일기장인가, 그렇다면 몇 코스짜리인가.
     * <p>
     * 마일스톤은 <b>완주 수를 3으로 내림</b>한 값이다 — 4코스를 끝낸 사람이 손으로 요청하면
     * 4가 아니라 3짜리 책을 받는다. 그래야 자동 적립분(3·6·9·12)과 같은 행을 가리켜
     * 같은 책이 두 권 생기지 않는다.
     *
     * @return 전자일기장이면 3·6·9·12 중 하나, 아니면 null(개인 소장본으로 처리한다)
     */
    private Integer interimMilestone(Long userId, String requestedType) {
        if (requestedType == null || !Ebook.INTERIM.equalsIgnoreCase(requestedType.trim())) {
            return null;
        }
        int completed = materialMapper.countCompletedCourses(userId);
        int floor = completed - (completed % INTERIM_COURSE_COUNT);
        return floor >= INTERIM_COURSE_COUNT ? floor : null;
    }

    /** 응답이 200 인지 202 인지 — 이미 완성돼 있으면 200 이다. 컨트롤러가 이것으로 상태코드를 고른다. */
    public boolean isAlreadyReady(EbookResponse response) {
        return Ebook.READY.equals(response.status());
    }

    /* ---------------- 만들기 (청소기가 부른다) ---------------- */

    /**
     * 한 권을 만든다. <b>있는 행을 잠근다</b>(챕터 8 §4 규칙) — 스케줄러가 둘이어도 같은 행을 두 번 집지 않는다.
     * 없는 범위를 잠그면 갭 잠금끼리 양립해 상호배제가 되지 않는다.
     * <p>
     * 실패는 예외로 올리지 않고 FAILED 로 적는다. 한 권이 실패했다고 청소기의 나머지 작업이
     * 멈추면 안 되고, 사용자에게는 "실패했으니 다시 눌러 달라" 를 보여 줘야 한다.
     */
    @Transactional
    public boolean build(Long ebookId) {
        Ebook row = ebookMapper.lockById(ebookId).orElse(null);
        if (row == null || !row.isRequested()) {
            return false;   // 다른 스레드가 이미 가져갔다
        }
        try {
            PdfBuilder.PdfResult pdf = pdfBuilder.build(
                    materialsOf(row.getUserId()), row.getEbookType(), row.getMilestone());
            String key = "EBOOK/%d/%d.pdf".formatted(row.getUserId(), ebookId);
            storageClient.put(key, pdf.bytes(), "application/pdf");
            ebookMapper.markReady(ebookId, key, pdf.pageCount(), pdf.bytes().length);
            trimReadyBooks(row.getUserId());
            log.info("전자책 완성. ebookId={}, pages={}, bytes={}", ebookId, pdf.pageCount(), pdf.bytes().length);
            return true;
        } catch (Exception e) {
            log.warn("전자책 조판 실패. ebookId={}", ebookId, e);
            ebookMapper.markFailed(ebookId, shorten(e.getMessage()));
            return false;
        }
    }

    /**
     * 남겨 두는 권수를 넘으면 가장 오래된 것부터 지운다. 파일은 바로 지우지 않고 큐에 넣는다 —
     * 이 트랜잭션이 되돌려질 수도 있는데 파일은 되돌아오지 않기 때문이다(챕터 1 보강과 같은 이유).
     * <b>인쇄 주문이 걸린 책은 후보에서 빠진다</b>(함정 7).
     */
    private void trimReadyBooks(Long userId) {
        List<Ebook> extra = ebookMapper.findTrimmable(userId, ebookProperties.maxReady());
        for (Ebook old : extra) {
            enqueueKeys(old, REASON_TRIMMED);
            ebookMapper.deleteById(old.getEbookId());
            log.info("전자책 상한 정리. userId={}, 지운 ebookId={}", userId, old.getEbookId());
        }
    }

    private void enqueueKeys(Ebook book, String reason) {
        for (String key : new String[]{book.getPdfKey(), book.getEpubKey()}) {
            if (key != null && !key.isBlank()) {
                storageOrphanMapper.enqueue(key, reason);
            }
        }
    }

    /* ---------------- 조회 ---------------- */

    public List<EbookResponse> getMyEbooks(Long userId) {
        return ebookMapper.findRowsByUserId(userId).stream()
                .map(EbookResponse::from)
                .toList();
    }

    /**
     * 1권 조회. <b>READY 일 때만</b> 서명 링크가 붙는다. 아니면 null 이고 에러가 아니다 —
     * 만드는 중이라는 것도 답이다.
     */
    public EbookResponse getOne(Long userId, Long ebookId) {
        EbookRow row = ebookMapper.findRowById(ebookId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EBOOK_4040));
        // 남의 책은 "없다" 고 답한다 — 있다는 사실 자체가 정보다(원고와 같은 원칙).
        if (!row.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.EBOOK_4040);
        }
        String url = Ebook.READY.equals(row.getStatus()) && row.getPdfKey() != null
                ? storageClient.presignGet(row.getPdfKey(), DOWNLOAD_SECONDS)
                : null;
        return EbookResponse.of(row, url);
    }

    public Ebook getOwned(Long userId, Long ebookId) {
        Ebook ebook = ebookMapper.findById(ebookId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EBOOK_4040));
        if (!ebook.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.EBOOK_4030);
        }
        return ebook;
    }

    /** 다운로드 링크 발급. 파일 키는 응답에 넣지 않고 짧게 사는 서명 URL 만 내준다. */
    public DownloadUrlResponse getDownloadUrl(Long userId, Long ebookId, String format) {
        Ebook ebook = getOwned(userId, ebookId);

        String normalized = format == null ? PDF : format.toUpperCase(Locale.ROOT);
        String fileKey = EPUB.equals(normalized) ? ebook.getEpubKey() : ebook.getPdfKey();

        if (fileKey == null || fileKey.isBlank()) {
            throw new BusinessException(ErrorCode.EBOOK_4090,
                    Ebook.READY.equals(ebook.getStatus())
                            ? "그 형식의 파일이 아직 없습니다."
                            : "아직 만들어지는 중입니다. 조금 뒤에 다시 시도해 주세요.");
        }

        long validity = storageClient.getPresignSeconds();
        return new DownloadUrlResponse(storageClient.presignGet(fileKey, validity), normalized, validity);
    }

    /* ---------------- 탈퇴 연쇄 (챕터 9 §2-5) ---------------- */

    /**
     * 탈퇴한 사람의 책을 지운다. 파일은 큐로 보낸다.
     * {@code NOT_SUPPORTED} 가 아닌 이유 — 탈퇴 연쇄 트랜잭션 안에서 함께 되돌아가야 한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int deleteAllForWithdrawal(Long userId) {
        List<Ebook> books = ebookMapper.findAllByUser(userId);
        books.forEach(b -> enqueueKeys(b, REASON_WITHDRAWN));
        return ebookMapper.deleteByUser(userId);
    }

    /* ---------------- 옛 흐름 (완주가 부르는 대기열) ---------------- */

    /**
     * 조판 대기열에 한 건 넣는다. 완주 시점에 CompletionService 가 호출한다.
     * 이미 있으면 그대로 둔다 — 재시도로 같은 책이 두 번 만들어지지 않게.
     */
    @Transactional
    public Ebook enqueue(Long userId, Long pilgrimageId, String ebookType) {
        return enqueue(userId, pilgrimageId, ebookType, null);
    }

    /**
     * 마일스톤이 있는 대기열(전자일기장 3·6·9·12).
     * <p>
     * 같은 종류라도 마일스톤이 다르면 <b>다른 책</b>이다. 사용자+종류로만 막으면 6코스 책이
     * 3코스 책과 같은 것으로 취급돼 만들어지지 않는다.
     */
    @Transactional
    public Ebook enqueue(Long userId, Long pilgrimageId, String ebookType, Integer milestone) {
        if (milestone != null) {
            var existing = ebookMapper.findAliveByUserTypeAndMilestone(userId, ebookType, milestone);
            if (existing.isPresent()) {
                return existing.get();
            }
        } else if (pilgrimageId != null) {
            var existing = ebookMapper.findByPilgrimageAndType(pilgrimageId, ebookType);
            if (existing.isPresent()) {
                return existing.get();
            }
        } else {
            var existing = ebookMapper.findAliveByUserAndType(userId, ebookType);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        Ebook ebook = Ebook.builder()
                .userId(userId)
                .pilgrimageId(pilgrimageId)
                .ebookType(ebookType)
                .milestone(milestone)
                .status(Ebook.REQUESTED)
                .build();
        ebookMapper.enqueue(ebook);
        log.info("전자책 대기열 등록. userId={}, type={}, pilgrimageId={}, milestone={}",
                userId, ebookType, pilgrimageId, milestone);
        return ebook;
    }

    /* ---------------- 관리자 ---------------- */

    /** 조판 결과 등록. pdf·epub 중 하나는 있어야 한다. */
    @Transactional
    public void registerFiles(Long ebookId, String pdfKey, String epubKey) {
        boolean hasPdf = pdfKey != null && !pdfKey.isBlank();
        boolean hasEpub = epubKey != null && !epubKey.isBlank();
        if (!hasPdf && !hasEpub) {
            throw new BusinessException(ErrorCode.COMMON_4000, "PDF 또는 EPUB 키 중 하나는 필요합니다.");
        }
        ebookMapper.findById(ebookId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EBOOK_4040));

        ebookMapper.updateFiles(ebookId, hasPdf ? pdfKey : null, hasEpub ? epubKey : null);
    }

    /* ---------------- 내부 ---------------- */

    EbookMaterials materialsOf(Long userId) {
        return new EbookMaterials(
                materialMapper.findNickname(userId),
                materialMapper.findStamps(userId),
                materialMapper.findLoosePhotos(userId),
                materialMapper.findThinkboxes(userId),
                materialMapper.findValidCerts(userId),
                materialMapper.countMeditationLogs(userId),
                materialMapper.sumMeditationSeconds(userId));
    }

    private EbookResponse toResponse(Ebook e) {
        EbookRow row = new EbookRow();
        row.setEbookId(e.getEbookId());
        row.setUserId(e.getUserId());
        row.setEbookType(e.getEbookType());
        row.setMilestone(e.getMilestone());
        row.setStatus(e.getStatus());
        row.setPdfKey(e.getPdfKey());
        row.setPageCount(e.getPageCount());
        row.setByteSize(e.getByteSize());
        row.setFailReason(e.getFailReason());
        return EbookResponse.from(row);
    }

    private String shorten(String message) {
        if (message == null) {
            return "알 수 없는 오류";
        }
        return message.length() <= 200 ? message : message.substring(0, 200);
    }
}
