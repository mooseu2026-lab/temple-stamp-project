package com.templestamp.thinkbox;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 생각상자.
 * <p>
 * 점수·순위·답장 컬럼이 없다. 비교하지 않는 기록이라는 설계다.
 * <p>
 * source 가 MISSION 이면 도장에서 넘어온 글이다. stamp.user_sentence 는 완료 후 고칠 수 없고,
 * 여기 사본만 고칠 수 있다. 인증서와 전자책이 참조하는 원본을 사후에 바꾸지 못하게 하면서도
 * 사람은 자기 글을 다듬을 수 있게 하려고 둘로 나눴다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Thinkbox {

    public static final String MISSION = "MISSION";
    public static final String DIRECT = "DIRECT";

    private Long thinkboxId;
    private Long userId;
    private String body;
    private String source;
    private Long stampId;
    private boolean isEdited;
    private Long courseId;
    private Long siteId;
    private boolean isPrivate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
