package com.templestamp.global.error;

import lombok.Getter;

import java.util.List;

/**
 * 서비스 계층에서 던지는 업무 예외. GlobalExceptionHandler 가 ErrorCode 그대로 응답으로 바꾼다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    /** null 이면 필드 단위 사유가 없다는 뜻. 있으면 GlobalExceptionHandler 가 error.fields 로 내보낸다. */
    private final List<ErrorResponse.FieldError> fields;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
        this.fields = null;
    }

    /** 기본 메시지 대신 상황을 특정한 메시지를 내보낼 때. code 는 그대로 유지된다. */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.fields = null;
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.message(), cause);
        this.errorCode = errorCode;
        this.fields = null;
    }

    /**
     * 필드별 사유를 함께 던진다. @Valid 가 못 잡는 조건("i18n 에 ko 가 있어야 한다" 처럼
     * 여러 필드를 함께 봐야 아는 것)을 프론트의 폼 오류 표시에 그대로 태우기 위한 것이다.
     * 응답 모양이 검증 실패와 같아지므로 프론트가 분기를 하나 더 만들지 않아도 된다.
     */
    public BusinessException(ErrorCode errorCode, List<ErrorResponse.FieldError> fields) {
        super(errorCode.message());
        this.errorCode = errorCode;
        this.fields = fields;
    }
}
