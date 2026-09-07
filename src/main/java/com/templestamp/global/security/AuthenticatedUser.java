package com.templestamp.global.security;

/**
 * SecurityContext 에 들어가는 로그인 사용자. Controller 에서 @AuthenticationPrincipal 로 받는다.
 * tier 는 클레임에 없으므로 여기도 없다 — 필요한 곳이 UserMapper.findTier 로 조회한다.
 */
public record AuthenticatedUser(Long userId, String email, String role) {

    /** 문자열 리터럴을 앞에 두어 role 이 null 이어도 NPE 가 나지 않게 한다. */
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
