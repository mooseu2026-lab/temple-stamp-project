package com.templestamp.admin.dto;

import java.time.LocalDateTime;

/**
 * 관리자 사용자 목록 한 줄(챕터 9 §6).
 * <p>
 * <b>비밀번호 해시·토큰·주소는 없다.</b> 관리자라도 그것들을 볼 이유가 없고,
 * 화면에 뜨면 어깨너머로도 새어 나간다. 탈퇴 계정은 익명화된 값이 그대로 실린다 —
 * 되살릴 수 없다는 것을 관리자도 그 화면에서 알아야 한다.
 * <p>
 * 정본 §6 이 적은 "마지막 로그인" 은 <b>없다</b> — 저장소에 그 칸이 없다.
 * 매 로그인마다 users 를 쓰는 비용이 붙는 일이라 이 챕터에서 만들지 않았다(ch9-ebook.md 에 보고).
 */
public record AdminUserResponse(
        Long userId,
        String email,
        String nickname,
        String role,
        String status,          // ACTIVE / DELETED
        LocalDateTime createdAt,
        LocalDateTime deletedAt // 살아 있으면 null
) {
}
