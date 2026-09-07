package com.templestamp.manuscript;

import com.templestamp.global.config.ManuscriptProperties;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.global.response.PageResponse;
import com.templestamp.manuscript.dto.ManuscriptCreateRequest;
import com.templestamp.manuscript.dto.ManuscriptResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 원고 등록·수정·제출·심사·퇴역(챕터 8 §2).
 * <p>
 * 규칙이 여럿이지만 하나로 모으면 이렇다 — <b>원고는 공개 콘텐츠이고, 한 번 나간 것은 되돌릴 수 없다.</b>
 * 그래서 들어올 때(금칙·중복·상한) 막고, 나갈 때(4-eyes) 한 번 더 보고, 물릴 때는 지우지 않고 퇴역시킨다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManuscriptService {

    /** 본문 길이 상한. 미션은 안내문이라 길고, 확장문구는 한 문장이라 짧다. */
    private static final int MISSION_BODY_MAX = 2000;
    private static final int EXT_BODY_MAX = 200;
    private static final int TITLE_MAX = 50;

    /**
     * 금칙. 원고는 그대로 사용자 화면과 전자책에 나가므로, 연락처·링크가 섞이면 되돌릴 수 없다.
     * 지우고 저장하지 않고 <b>거부</b>하는 이유는, 지운 자리가 어색해도 쓴 사람은 모르기 때문이다.
     */
    private static final Pattern URL = Pattern.compile("(?i)(https?://|www\\.)");
    private static final Pattern PHONE = Pattern.compile("0\\d{1,2}-?\\d{3,4}-?\\d{4}");
    private static final Pattern EMAIL = Pattern.compile("[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}");
    private static final Pattern HTML = Pattern.compile("<[^>]+>");

    private final ManuscriptMapper manuscriptMapper;
    private final ManuscriptProperties manuscriptProperties;

    /* ---------------- 등록·수정 ---------------- */

    /**
     * 등록. 변형 번호는 <b>서버가</b> 매긴다 — 편집자가 세면 두 사람이 같은 번호를 쓴다.
     * 번호를 매기기 전에 <b>있는 행 하나</b>를 잠그고(사찰 행 또는 기본 원고 행), 유니크가
     * 마지막 방어선이다(함정 2). 없는 행의 범위를 잠그면 갭 잠금끼리 양립해 교착만 남는다 —
     * 그 실측 기록은 {@code ManuscriptMapper.xml} 의 lockKeyOwner 주석에 있다.
     */
    @Transactional
    public ManuscriptResponse create(Long authorId, ManuscriptCreateRequest request) {
        String title = clean(request.title());
        String body = clean(request.body());
        validateContent(request.kind(), title, body);

        long siteKey = request.siteId() == null ? 0L : request.siteId();
        manuscriptMapper.lockKeyOwner(request.siteId(), request.verseNo(), request.kind());

        if (manuscriptMapper.countSameBody(siteKey, request.verseNo(), request.kind(), body) > 0) {
            throw new BusinessException(ErrorCode.MS_4091);
        }
        int active = manuscriptMapper.countActive(siteKey, request.verseNo(), request.kind());
        int max = request.siteId() == null ? 1 : manuscriptProperties.maxVariants();
        if (active >= max) {
            throw new BusinessException(ErrorCode.MS_4092, request.siteId() == null
                    ? "기본 원고는 구·종류마다 한 편입니다 — 먼저 퇴역시키세요."
                    : "변형 상한(%d)에 도달했습니다 — 먼저 퇴역시키세요.".formatted(max));
        }

        Manuscript manuscript = Manuscript.builder()
                .siteId(request.siteId())
                .verseNo(request.verseNo())
                .kind(request.kind())
                .variantNo(manuscriptMapper.maxVariantNo(siteKey, request.verseNo(), request.kind()) + 1)
                .status(Manuscript.DRAFT)
                .title(title)
                .body(body)
                .authorId(authorId)
                .build();
        try {
            manuscriptMapper.insert(manuscript);
        } catch (DuplicateKeyException e) {
            // 잠금을 뚫고 같은 번호가 들어온 경우. 사용자에게는 상한과 같은 말로 알린다.
            throw new BusinessException(ErrorCode.MS_4092, "같은 자리에 동시에 등록됐습니다. 다시 시도해 주세요.");
        }
        log.info("원고 등록. id={}, site={}, verse={}, kind={}, variant={}",
                manuscript.getManuscriptId(), request.siteId(), request.verseNo(),
                request.kind(), manuscript.getVariantNo());
        return toResponse(manuscript, authorId);
    }

    /** 수정 — 작성자 본인, DRAFT·REJECTED 만. 반려본을 고치면 자동으로 DRAFT 로 돌아간다. */
    @Transactional
    public ManuscriptResponse update(Long editorId, Long manuscriptId, String rawTitle, String rawBody) {
        Manuscript manuscript = ownedOrNotFound(editorId, manuscriptId);
        String title = clean(rawTitle);
        String body = clean(rawBody);
        validateContent(manuscript.getKind(), title, body);

        if (!Manuscript.DRAFT.equals(manuscript.getStatus())
                && !Manuscript.REJECTED.equals(manuscript.getStatus())) {
            throw new BusinessException(ErrorCode.MS_4090, "제출·승인된 원고는 고칠 수 없습니다.");
        }
        if (manuscriptMapper.updateContent(manuscriptId, title, body) == 0) {
            throw new BusinessException(ErrorCode.MS_4090);
        }
        return get(manuscriptId, editorId);
    }

    @Transactional
    public void submit(Long editorId, Long manuscriptId) {
        ownedOrNotFound(editorId, manuscriptId);
        if (manuscriptMapper.markSubmitted(manuscriptId) == 0) {
            throw new BusinessException(ErrorCode.MS_4090, "작성 중인 원고만 제출할 수 있습니다.");
        }
    }

    /* ---------------- 심사 ---------------- */

    /**
     * 승인. <b>자기 원고는 승인할 수 없다</b>(4-eyes) — 쓴 사람과 내보내는 사람이 같으면
     * 검토라는 단계가 이름만 남는다. 관리자도 원고를 쓸 수 있으므로 실제로 걸린다.
     */
    @Transactional
    public void approve(Long adminId, Long manuscriptId) {
        Manuscript manuscript = existing(manuscriptId);
        requireNotAuthor(adminId, manuscript);

        if (manuscriptMapper.markApproved(manuscriptId, adminId) == 0) {
            throw new BusinessException(ErrorCode.MS_4090, "제출된 원고만 승인할 수 있습니다.");
        }
        if (manuscript.isDefault()) {
            // 기본 원고는 구·종류마다 한 편이다. 새것이 서면 옛것이 물러난다.
            int retired = manuscriptMapper.retirePreviousDefault(
                    manuscript.getVerseNo(), manuscript.getKind(), manuscriptId);
            if (retired > 0) {
                log.info("기본 원고 교체. verse={}, kind={}, 물러난 편수={}",
                        manuscript.getVerseNo(), manuscript.getKind(), retired);
            }
        }
    }

    @Transactional
    public void reject(Long adminId, Long manuscriptId, String reason) {
        Manuscript manuscript = existing(manuscriptId);
        requireNotAuthor(adminId, manuscript);

        if (manuscriptMapper.markRejected(manuscriptId, adminId, reason.trim()) == 0) {
            throw new BusinessException(ErrorCode.MS_4090, "제출된 원고만 반려할 수 있습니다.");
        }
    }

    /** 퇴역 — 삭제가 아니다. 그 원고를 참조하는 도장의 글은 그대로 남는다. */
    @Transactional
    public void retire(Long adminId, Long manuscriptId) {
        existing(manuscriptId);
        if (manuscriptMapper.markRetired(manuscriptId) == 0) {
            throw new BusinessException(ErrorCode.MS_4090, "승인된 원고만 퇴역시킬 수 있습니다.");
        }
        log.info("원고 퇴역. id={}, adminId={}", manuscriptId, adminId);
    }

    /* ---------------- 조회 ---------------- */

    /**
     * 목록. 편집자는 자기 것만 본다 — 남의 초안·반려 사유는 남의 것이다.
     * 관리자는 전체를 본다(심사해야 하므로).
     */
    public PageResponse<ManuscriptResponse> list(Long authorFilter, Long siteId, Integer verseNo,
                                                 String kind, String status, int page, int size) {
        long total = manuscriptMapper.countRows(authorFilter, siteId, verseNo, kind, status);
        List<ManuscriptResponse> items = manuscriptMapper
                .findRows(authorFilter, siteId, verseNo, kind, status, page * size, size)
                .stream().map(ManuscriptResponse::from).toList();
        return PageResponse.of(items, page, size, total);
    }

    /**
     * 한 건 조회. 목록과 <b>필드가 같은</b> 응답을 준다 — 프론트가 두 모양을 다루지 않게.
     * 사찰 이름·작성자 닉네임은 여기서 비어 있다(도메인만 읽는다). 목록에서는 조인해 채운다.
     */
    public ManuscriptResponse get(Long manuscriptId, Long viewerId) {
        return toResponse(existing(manuscriptId), viewerId);
    }

    /* ---------------- 내부 ---------------- */

    /** 남의 원고는 "없다" 고 답한다 — 있다는 사실 자체가 정보다. */
    private Manuscript ownedOrNotFound(Long editorId, Long manuscriptId) {
        Manuscript manuscript = manuscriptMapper.findById(manuscriptId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MS_4040));
        if (!manuscript.getAuthorId().equals(editorId)) {
            throw new BusinessException(ErrorCode.MS_4040);
        }
        return manuscript;
    }

    private Manuscript existing(Long manuscriptId) {
        return manuscriptMapper.findById(manuscriptId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MS_4040));
    }

    private void requireNotAuthor(Long reviewerId, Manuscript manuscript) {
        if (manuscript.getAuthorId().equals(reviewerId)) {
            throw new BusinessException(ErrorCode.MS_4030);
        }
    }

    /**
     * 내용 검증. 길이는 종류마다 다르고, 금칙은 종류와 무관하다.
     * 공백만 있는 본문을 막으려고 앞뒤를 먼저 다듬는다.
     */
    void validateContent(String kind, String title, String body) {
        if (title.isEmpty() || body.isEmpty()) {
            throw new BusinessException(ErrorCode.COMMON_4000, "제목과 본문을 모두 입력해주세요.");
        }
        if (title.length() > TITLE_MAX) {
            throw new BusinessException(ErrorCode.COMMON_4000, "제목은 %d자 이하로 입력해주세요.".formatted(TITLE_MAX));
        }
        int bodyMax = Manuscript.MISSION.equals(kind) ? MISSION_BODY_MAX : EXT_BODY_MAX;
        if (body.length() > bodyMax) {
            throw new BusinessException(ErrorCode.COMMON_4000,
                    "%s 본문은 %d자 이하로 입력해주세요.".formatted(kind, bodyMax));
        }
        String whole = title + " " + body;
        if (URL.matcher(whole).find() || PHONE.matcher(whole).find() || EMAIL.matcher(whole).find()) {
            throw new BusinessException(ErrorCode.MS_4002);
        }
        if (HTML.matcher(whole).find()) {
            throw new BusinessException(ErrorCode.MS_4002, "원고에 태그를 넣을 수 없습니다 — 글자만 담깁니다.");
        }
    }

    static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private ManuscriptResponse toResponse(Manuscript m, Long viewerId) {
        return new ManuscriptResponse(m.getManuscriptId(), m.getSiteId(), null, m.getVerseNo(),
                m.getKind(), m.getVariantNo(), m.getStatus(), m.getTitle(), m.getBody(),
                m.getAuthorId(), null, m.getRejectReason(), m.getReviewedAt(), m.getRetiredAt(),
                m.getCreatedAt());
    }
}
