package com.cakeshop.global.error;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 도메인 비즈니스 예외 → 각 도메인의 ErrorCode
    @ExceptionHandler(BusinessException.class)
    public String handleBusiness(BusinessException e, Model model, HttpServletResponse response) {
        return render(e.getErrorCode(), model, response);
    }

    // 검증 실패(@Valid) → 공통 INVALID_INPUT
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public String handleValidation(Model model, HttpServletResponse response) {
        return render(CommonErrorCode.INVALID_INPUT, model, response);
    }

    // 권한 없음 → 공통 FORBIDDEN
    @ExceptionHandler(AccessDeniedException.class)
    public String handleAccessDenied(Model model, HttpServletResponse response) {
        return render(CommonErrorCode.FORBIDDEN, model, response);
    }

    // 존재하지 않는 정적 리소스(favicon.ico 등) → 조용히 404. 스택트레이스를 남기지 않는다.
    @ExceptionHandler(NoResourceFoundException.class)
    public String handleNoResource(NoResourceFoundException e, Model model, HttpServletResponse response) {
        log.debug("No static resource: {}", e.getResourcePath());
        return render(CommonErrorCode.NOT_FOUND, model, response);
    }

    // 예상하지 못한 오류 → 공통 INTERNAL_ERROR (최후의 방어선)
    @ExceptionHandler(Exception.class)
    public String handleUnexpected(Exception e, Model model, HttpServletResponse response) {
        log.error("Unhandled exception", e);
        return render(CommonErrorCode.INTERNAL_ERROR, model, response);
    }

    // ErrorCode가 선언한 상태/코드/메시지를 실제 MVC 응답에 반영한다.
    private String render(ErrorCode errorCode, Model model, HttpServletResponse response) {
        int status = errorCode.status();
        response.setStatus(status);
        model.addAttribute("code", errorCode.code());
        model.addAttribute("message", errorCode.message());
        return viewFor(status);
    }

    // 404는 전용 페이지, 그 외 4xx는 공통 클라이언트 오류 페이지, 5xx는 서버 오류 페이지.
    private String viewFor(int status) {
        if (status == 404) {
            return "error/404";
        }
        if (status >= 400 && status < 500) {
            return "error/4xx";
        }
        return "error/500";
    }
}
