package com.cakeshop.global.error;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    /**
     * 상황값(건수 등)을 담아 화면에 그대로 보여줄 때 쓴다.
     * 오류 페이지는 {@code errorCode.message()}를 쓰므로, 이 메시지는 필드 오류로 되돌리는
     * 컨트롤러({@code bindingResult.rejectValue(..., e.getMessage())})에서만 드러난다.
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 외부 연동 실패처럼 원인 예외가 있는 경우에 쓴다. 화면에는 {@code errorCode.message()}가 나가고
     * 원인 스택은 로그({@code GlobalExceptionHandler})에만 남는다 — 외부 오류 문구를 사용자에게
     * 그대로 노출하지 않는다.
     */
    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.message(), cause);
        this.errorCode = errorCode;
    }
}
