package com.templestamp.certificate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * certificate 테이블 도메인 클래스 — 인증서.
 * PILGRIMAGE : 코스 완주. 유효한 것은 순례 하나당 한 장(uk_certificate_valid_pilgrimage)
 * HOEHYANG   : 전 사찰 회향. 순례에 매이지 않으므로 pilgrimageId 가 NULL 이다
 * <p>
 * 상태는 {@code VALID → REVOKED} 한 방향이고 되돌리지 않는다(챕터 7 §3-2).
 * 회수는 행을 지우는 것이 아니다 — 그 번호를 이미 남에게 보여 줬을 수 있어,
 * 공개 진위 확인에서 "그런 번호가 있었고 지금은 무효" 로 계속 조회돼야 한다.
 * 완주가 다시 성립하면 같은 번호를 되살리지 않고 <b>새 번호로 재발행</b>한다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Certificate {

    public static final String PILGRIMAGE = "PILGRIMAGE";
    public static final String HOEHYANG = "HOEHYANG";

    public static final String VALID = "VALID";
    public static final String REVOKED = "REVOKED";

    /** 회수 사유 셋. 완주 취소(자동) · 관리자 회수 · 회원 탈퇴. */
    public static final String COMPLETION_CANCELED = "COMPLETION_CANCELED";
    public static final String ADMIN = "ADMIN";
    public static final String USER_WITHDRAWN = "USER_WITHDRAWN";

    private Long certificateId;
    private Long userId;
    private Long pilgrimageId;
    private String certType;
    private String status;
    private String serialNo;
    private LocalDateTime issuedAt;
    private LocalDateTime revokedAt;
    private String revokeReason;
    private String fileKey;
}
