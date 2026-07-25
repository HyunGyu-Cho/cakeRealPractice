package com.cakeshop.domain.chat.controller;

import com.cakeshop.domain.chat.error.ChatErrorCode;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.CommonErrorCode;
import com.cakeshop.global.error.ErrorCode;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice(assignableTypes = {
    ChatApiController.class,
    ChatAdminApiController.class
})
public class ChatApiExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, String>> business(BusinessException exception) {
        return response(exception.getErrorCode());
    }

    @ExceptionHandler({BindException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<Map<String, String>> validation(Exception exception) {
        return response(CommonErrorCode.INVALID_INPUT);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> tooLarge(MaxUploadSizeExceededException exception) {
        return response(ChatErrorCode.IMAGE_TOO_LARGE);
    }

    private ResponseEntity<Map<String, String>> response(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.status())
            .body(Map.of("code", errorCode.code(), "message", errorCode.message()));
    }
}
