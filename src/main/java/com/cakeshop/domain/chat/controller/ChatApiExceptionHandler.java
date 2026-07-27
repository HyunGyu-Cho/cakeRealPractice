package com.cakeshop.domain.chat.controller;

import com.cakeshop.domain.chat.error.ChatErrorCode;
import com.cakeshop.global.error.ErrorCode;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 채팅 API의 고유 오류만 처리한다. 나머지({@code BusinessException}·검증 실패·예상 못한 오류)는
 * {@code global/error/ApiExceptionHandler}가 모든 JSON 컨트롤러에 같은 모양으로 돌려준다.
 *
 * <p>업로드 상한 초과만 여기 남는 이유: 톰캣이 컨트롤러 진입 전에 던지므로 도메인 검증
 * ({@code ChatService.validateImage})이 돌지 못하고, 공통 처리의 일반 문구 대신 "이미지 용량"
 * 안내를 내보내야 한다. {@code assignableTypes}가 더 구체적이라 공통 advice보다 이긴다.
 */
@RestControllerAdvice(assignableTypes = {
    ChatApiController.class,
    ChatAdminApiController.class
})
public class ChatApiExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> tooLarge(MaxUploadSizeExceededException exception) {
        return response(ChatErrorCode.IMAGE_TOO_LARGE);
    }

    private ResponseEntity<Map<String, String>> response(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.status())
            .body(Map.of("code", errorCode.code(), "message", errorCode.message()));
    }
}
