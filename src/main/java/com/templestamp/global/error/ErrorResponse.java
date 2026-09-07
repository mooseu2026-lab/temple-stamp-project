package com.templestamp.global.error;

import java.util.List;

/**
 * 요청이 실패했을 때 에러 코드·안내 문구·필드별 검증 결과를 담는 DTO Record입니다.
 * JSON 키는 규약 §1·06_DTO 대로 "fields" — 이름이 다르면 프론트 검증 오류 표시가 통째로 안 나온다.
 * §8(null 필드도 키를 내려준다)에 따라 @JsonInclude 없이 검증 오류가 아니면 "fields": null 로 나간다.
 * [사용 위치] global/error — ErrorCode·BusinessException·GlobalExceptionHandler 와 같은 패키지
 */
public record ErrorResponse(
        String code,                      // 예: AUTH-4011, CERT-4041
        String message,                   // 사용자에게 그대로 보여줄 안내 문구
        List<FieldError> fields           // @Valid 실패 시에만 채움, 그 외 null
) {
    /** MethodArgumentNotValidException 의 필드 한 건 */
    public record FieldError(
            String field,                 // 실패한 필드명 (예: "email")
            String reason                 // 어노테이션 message 값
    ) {}

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, null);
    }

    public static ErrorResponse ofValidation(String code, String message, List<FieldError> fields) {
        return new ErrorResponse(code, message, fields);
    }
}
