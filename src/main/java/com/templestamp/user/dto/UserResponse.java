package com.templestamp.user.dto;

import com.templestamp.user.User;

import java.time.LocalDateTime;

/**
 * 사용자 정보를 클라이언트에 내려주는 DTO Record입니다.
 * 비밀번호 해시·실패 횟수·잠금 시각 같은 내부 컬럼을 담지 않아
 * 계정 정보가 밖으로 나가지 않게 막는 역할을 합니다.
 * [사용 위치] UserService / AuthService 조립 → Controller 반환
 */
public record UserResponse(
        Long userId,
        String email,
        String nickname,
        String role,
        String tier,                 // [가정] NULL 대신 항상 값(AGE30)으로 내려 프론트 분기 제거
        String locale,
        Boolean notificationEnabled,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getUserId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole(),
                user.tierOrDefault(),
                user.getLocale(),
                user.getNotificationEnabled(),
                user.getCreatedAt());
    }
}
