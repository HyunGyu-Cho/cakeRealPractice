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
}
