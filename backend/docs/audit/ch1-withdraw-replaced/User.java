package com.templestamp.user;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * users 테이블 도메인 클래스 — 회원
 * MyBatis resultMap 대상. map-underscore-to-camel-case 로 컬럼명 자동 매핑.
 */
@Getter
@Setter
@NoArgsConstructor
public class User {

    private Long userId;
    private String email;  // 소문자 정규화
    private String password;  // BCrypt
    private String nickname;
    private String role;
    private String tier;
    private String locale;
    private Boolean notificationEnabled;
    private Integer loginFailCount;
    private LocalDateTime lockedUntil;  // 5회 실패 시 now()+15분
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";

    /** NULL이면 AGE30으로 취급. JWT에 넣지 않음 */
    public String tierOrDefault() { return tier == null ? "AGE30" : tier; }

    public boolean isLockedNow() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    public boolean isAdmin() { return ROLE_ADMIN.equals(role); }
}
