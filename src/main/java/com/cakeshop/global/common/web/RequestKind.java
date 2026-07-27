package com.cakeshop.global.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 현재 요청이 <b>화면(뷰)을 렌더하는 요청</b>인지 판정한다.
 *
 * <p>공통 헤더용 {@code @ModelAttribute}(장바구니 수량·미읽음 알림 수)는 화면에서만 쓰인다.
 * 그런데 {@code @ControllerAdvice}로는 JSON 컨트롤러를 걸러낼 수 없다 —
 * {@code annotations = Controller.class}를 지정해도 {@code @RestController}가
 * {@code @Controller}를 <b>메타 애노테이션으로 갖기 때문에 함께 잡힌다</b>.
 * 그래서 selector 대신 요청 시점에 핸들러를 보고 판정한다.
 *
 * <p>{@code BEST_MATCHING_HANDLER_ATTRIBUTE}는 DispatcherServlet의 핸들러 조회 단계에서
 * 채워지므로 {@code @ModelAttribute}가 돌 때 이미 사용할 수 있다.
 */
public final class RequestKind {

    private RequestKind() {
    }

    /** 판정할 수 없으면 {@code true}(화면)로 본다 — 모델이 비어 화면이 깨지는 쪽보다 안전하다. */
    public static boolean rendersView(HttpServletRequest request) {
        Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        // @RestController 는 @ResponseBody 를 메타 애노테이션으로 갖는다.
        return !handlerMethod.hasMethodAnnotation(ResponseBody.class)
            && !AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), ResponseBody.class);
    }
}
