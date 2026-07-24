package com.cakeshop.global.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

// 로그인 성공 시 역할에 따라 기본 진입점을 분기한다.
// 보호 페이지 접근 후 로그인한 경우(저장된 요청)에는 원래 가려던 경로를 우선한다.
public class RoleBasedAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException, ServletException {
        boolean isAdmin = authentication.getAuthorities().stream()
            .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        // 관리자는 대시보드, 그 외 회원은 마이페이지로 보낸다. (저장된 요청이 없을 때만 적용)
        setDefaultTargetUrl(isAdmin ? "/admin" : "/mypage");
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
