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
 * DTO 는 content / isPublic 이라는 이름을 쓰고 DB 는 body / is_private 이다.
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
                .isPrivate(true)          // 기본 비공개. 공개는 사용자가 따로 켠다
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

    public PageResponse<ThinkboxResponse> getMine(Long userId, int page, int size) {
        long total = thinkboxMapper.countByUserId(userId);
        List<ThinkboxResponse> items = thinkboxMapper.findByUserId(userId, page * size, size).stream()
                .map(ThinkboxResponse::from)
                .toList();
        return PageResponse.of(items, page, size, total);
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
        if (request.isPublic() != null) {
            if (request.isPublic() && !userService.isEbookPublicAgreed(userId)) {
                throw new BusinessException(ErrorCode.USER_4030,
                        "전자책 공개 수록에 동의해야 글을 공개할 수 있습니다.");
            }
            isPrivate = !request.isPublic();
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
