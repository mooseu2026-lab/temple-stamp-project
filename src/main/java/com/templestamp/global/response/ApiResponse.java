package com.templestamp.global.response;

import com.templestamp.global.error.ErrorResponse;

import java.time.OffsetDateTime;

/**
 * 모든 API 응답을 동일한 형태로 감싸주는 공통 래퍼 DTO Record입니다.
 * 04_공통규약 §8 — null 필드도 키를 내려준다(Jackson Include.ALWAYS 기본). 그래서 @JsonInclude 를 달지 않는다.
 * 성공 응답에도 "error": null 이 명시적으로 찍힌다(§1 예시). 클라이언트가 스키마를 예측 가능하게 하기 위함.
 * [사용 위치] Controller 전 계층의 반환 타입 + GlobalExceptionHandler + JsonAuthenticationEntryPoint/DeniedHandler
 */
public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorResponse error,
        OffsetDateTime timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, OffsetDateTime.now());
    }

    /** 본문 없는 성공(삭제·로그아웃 등) — "data": null 로 나간다 */
    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null, OffsetDateTime.now());
    }

    public static ApiResponse<Void> fail(ErrorResponse error) {
        return new ApiResponse<>(false, null, error, OffsetDateTime.now());
    }
}
