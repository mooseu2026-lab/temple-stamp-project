package com.templestamp.thinkbox;

import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.global.response.PageResponse;
import com.templestamp.thinkbox.dto.ThinkboxCreateRequest;
import com.templestamp.thinkbox.dto.ThinkboxResponse;
import com.templestamp.thinkbox.dto.ThinkboxRow;
import com.templestamp.thinkbox.dto.ThinkboxUpdateRequest;
import com.templestamp.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 생각상자. 점수·순위·답장이 없는 기록이다.
 * <p>
 * DTO 는 content / isPrivate 이라는 이름을 쓰고 DB 는 body / is_private 이다.
 * 예전에는 DTO 만 isPublic(반대 뜻)이라, 어디선가 한 번만 뒤집으면 조용히 반대로 저장됐다.
 * 사용자에게는 "공개할까요?" 를 묻는 편이 자연스럽고, DB 에는 기본값이 비공개(0)여야
 * 컬럼을 빠뜨렸을 때 안전한 쪽으로 떨어지기 때문에 두 이름을 일부러 뒤집어 두었다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ThinkboxService {

    private final ThinkboxMapper thinkboxMapper;
    private final UserService userService;

    @Transactional
    public Long create(Long userId, ThinkboxCreateRequest request) {
        Thinkbox thinkbox = Thinkbox.builder()
                .userId(userId)
                .body(request.content())
                .source(Thinkbox.DIRECT)
                .courseId(request.courseId())
                .siteId(request.siteId())
                // 안 보내면 비공개. 공개는 사용자가 따로 켜는 것이다.
                .isPrivate(request.isPrivate() == null || request.isPrivate())
                .build();
        thinkboxMapper.save(thinkbox);
        return thinkbox.getThinkboxId();
    }

    /**
     * 도장에서 넘어온 사본을 만든다. 도장 하나에 사본 하나뿐이라(uk_thinkbox_stamp)
     * 같은 도장으로 다시 불려도 새 글이 생기지 않는다.
     *
     * @return 새로 만들었든 이미 있었든, 그 도장의 생각상자 글 id
     */
    @Transactional
    public Long createFromMission(Long userId, Long stampId, Long courseId, Long siteId, String sentence) {
        thinkboxMapper.saveFromMission(Thinkbox.builder()
                .userId(userId)
                .body(sentence)
                .source(Thinkbox.MISSION)
                .stampId(stampId)
                .courseId(courseId)
                .siteId(siteId)
                .isPrivate(true)
                .build());

        return thinkboxMapper.findByStampId(stampId)
                .map(Thinkbox::getThinkboxId)
                .orElse(null);
    }

    /**
     * 내 글 목록. {@code sort} 는 date(최신순) · course(코스별) · site(사찰별) 셋뿐이고
     * 그 밖의 값은 컨트롤러의 {@code @Pattern} 이 400 으로 막는다 — SQL 에 문자열을 그대로 꽂지 않기 위해서다.
     */
    public PageResponse<ThinkboxResponse> getMine(Long userId, String sort, int page, int size) {
        long total = thinkboxMapper.countByUserId(userId);
        List<ThinkboxResponse> items = thinkboxMapper.findByUserId(userId, sort, page * size, size).stream()
                .map(ThinkboxResponse::from)
                .toList();
        return PageResponse.of(items, page, size, total);
    }

    /**
     * 사찰에 묶인 DIRECT 기록은 그 절에 하나뿐이다 — 있으면 갈아 끼우고 없으면 만든다.
     * {@link com.templestamp.photo.PhotoService} 가 사진과 <b>같은 트랜잭션</b>에서 부른다.
     */
    @Transactional
    public void upsertDirectForSite(Long userId, Long siteId, Long courseId, String content, boolean isPrivate) {
        Thinkbox existing = thinkboxMapper.findDirectBySite(userId, siteId).orElse(null);
        if (existing == null) {
            thinkboxMapper.save(Thinkbox.builder()
                    .userId(userId).body(content).source(Thinkbox.DIRECT)
                    .courseId(courseId).siteId(siteId).isPrivate(isPrivate)
                    .build());
            return;
        }
        // update 의 SET 순서가 is_edited 를 지킨다 — 본문이 실제로 바뀔 때만 켜진다.
        thinkboxMapper.update(existing.getThinkboxId(), content, isPrivate);
    }

    /**
     * 여섯 달 전 오늘 쓴 글. <b>없는 것이 정상</b>이라 예외를 던지지 않고 null 을 돌려준다
     * (컨트롤러가 200 + {@code data: null}). 404 로 만들면 앱이 오류 화면을 띄운다.
     */
    public ThinkboxResponse flashback(Long userId) {
        return thinkboxMapper.findFlashback(userId).map(ThinkboxResponse::from).orElse(null);
    }

    /**
     * 본문을 고치면 isEdited 가 켜진다. 도장의 원본 문장(stamp.user_sentence)은 그대로 남는다.
     * 공개로 바꾸려면 전자책 공개 수록 약관에 동의돼 있어야 한다 — 글 하나만 공개로 돌려 놓고
     * 약관에는 동의하지 않은 상태를 만들 수 없게.
     */
    @Transactional
    public void update(Long userId, Long thinkboxId, ThinkboxUpdateRequest request) {
        Thinkbox thinkbox = getOwned(userId, thinkboxId);

        boolean isPrivate = thinkbox.isPrivate();
        if (request.isPrivate() != null) {
            if (!request.isPrivate() && !userService.isEbookPublicAgreed(userId)) {
                throw new BusinessException(ErrorCode.USER_4030,
                        "전자책 공개 수록에 동의해야 글을 공개할 수 있습니다.");
            }
            isPrivate = request.isPrivate();
        }

        thinkboxMapper.update(
                thinkbox.getThinkboxId(),
                request.content() != null ? request.content() : thinkbox.getBody(),
                isPrivate);
    }

    @Transactional
    public void delete(Long userId, Long thinkboxId) {
        Thinkbox thinkbox = getOwned(userId, thinkboxId);
        thinkboxMapper.delete(thinkbox.getThinkboxId());
    }

    /** 전자책 편집에 쓰는 목록. courseId 가 null 이면 그 사용자의 전체 글을 모은다. */
    public List<ThinkboxRow> getPublishable(Long userId, Long courseId) {
        return thinkboxMapper.findPublishable(userId, courseId);
    }

    public long countByUser(Long userId) {
        return thinkboxMapper.countByUserId(userId);
    }

    private Thinkbox getOwned(Long userId, Long thinkboxId) {
        Thinkbox thinkbox = thinkboxMapper.findById(thinkboxId)
                .orElseThrow(() -> new BusinessException(ErrorCode.THINKBOX_4040));
        if (!thinkbox.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.THINKBOX_4030);
        }
        return thinkbox;
    }
}
