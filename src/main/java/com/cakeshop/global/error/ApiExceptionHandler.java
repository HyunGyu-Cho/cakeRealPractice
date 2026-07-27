package com.cakeshop.global.error;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * JSON API 전용 예외 처리. 오류를 {@code {"code":..., "message":...}} 한 가지 모양으로 돌려준다.
 *
 * <p>{@code annotations = RestController.class}로 <b>JSON 컨트롤러만</b> 잡는다. SSR 화면
 * ({@code @Controller})은 계속 {@link GlobalExceptionHandler}가 오류 페이지를 렌더한다 —
 * HTML/JSON 분기를 selector로 끝내고 content negotiation 코드를 쓰지 않는다.
 *
 * <p>이것이 없으면 JSON 요청에 <b>HTML 오류 페이지</b>가 나간다. fetch 호출자는 서버가 준 메시지를
 * 읽을 수 없고, 서버는 XHR 응답으로 화면 하나를 통째로 렌더하는 헛일을 한다.
 * 도메인이 고유 메시지를 주고 싶으면 {@code assignableTypes}를 지정한 자기 advice를 두면 된다
 * (더 구체적인 selector가 이긴다 — 예: {@code ChatApiExceptionHandler}).
 */
@RestControllerAdvice(annotations = RestController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

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
        return response(CommonErrorCode.INVALID_INPUT);
    }

    /** 예상하지 못한 오류도 JSON으로 돌려준다 — 여기서 놓치면 HTML 오류 페이지가 나간다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unexpected(Exception exception) {
        log.error("Unhandled exception in JSON API", exception);
        return response(CommonErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<Map<String, String>> response(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.status())
            .body(Map.of("code", errorCode.code(), "message", errorCode.message()));
    }
}
