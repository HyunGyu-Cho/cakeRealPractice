package com.cakeshop.domain.member.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.member.dto.form.PasswordForm;
import com.cakeshop.domain.member.dto.form.ProfileUpdateForm;
import com.cakeshop.domain.member.dto.view.MemberProfileView;
import com.cakeshop.domain.member.error.MemberErrorCode;
import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.security.MemberDetails;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
class MyPageControllerTests {

    private static final MemberDetails LOGIN_MEMBER = new MemberDetails(
        1L, "user@cakeshop.local", "hash", List.of(new SimpleGrantedAuthority("ROLE_USER")));

    @Mock
    private MemberService memberService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // standalone MockMvc에는 시큐리티 컨텍스트가 없어 @AuthenticationPrincipal을 직접 해석해 준다.
        HandlerMethodArgumentResolver principalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType() == MemberDetails.class;
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return LOGIN_MEMBER;
            }
        };
        mockMvc = MockMvcBuilders.standaloneSetup(new MyPageController(memberService))
            .setCustomArgumentResolvers(principalResolver)
            .build();
    }

    @Test
    void myPageLoadsProfile() throws Exception {
        when(memberService.getProfile(1L)).thenReturn(profile());

        mockMvc.perform(get("/mypage"))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/member/mypage"))
            .andExpect(model().attributeExists("profile"));
    }

    @Test
    void profilePageLoadsFormsAndProfile() throws Exception {
        when(memberService.getProfile(1L)).thenReturn(profile());

        mockMvc.perform(get("/mypage/profile"))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/member/profile-edit"))
            .andExpect(model().attributeExists("profile", "profileForm", "passwordForm"));
    }

    @Test
    void invalidProfileUpdateRendersSameFormWithoutCallingService() throws Exception {
        when(memberService.getProfile(1L)).thenReturn(profile());

        mockMvc.perform(post("/mypage/profile").param("nickname", ""))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/member/profile-edit"))
            .andExpect(model().attributeHasFieldErrors("profileForm", "nickname", "phone"));

        verify(memberService, never()).updateProfile(any(), any(ProfileUpdateForm.class));
    }

    @Test
    void validProfileUpdateRedirectsWithFlashMessage() throws Exception {
        mockMvc.perform(post("/mypage/profile")
                .param("nickname", "새이름")
                .param("phone", "010-9999-8888"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/mypage/profile"))
            .andExpect(flash().attribute("successMessage", "회원 정보를 수정했습니다."));

        verify(memberService).updateProfile(eq(1L), any(ProfileUpdateForm.class));
    }

    @Test
    void wrongCurrentPasswordReturnsFieldError() throws Exception {
        when(memberService.getProfile(1L)).thenReturn(profile());
        doThrow(new BusinessException(MemberErrorCode.PASSWORD_MISMATCH))
            .when(memberService).changePassword(eq(1L), any(PasswordForm.class));

        mockMvc.perform(post("/mypage/password")
                .param("currentPassword", "wrong")
                .param("newPassword", "NewPassword1!")
                .param("newPasswordConfirm", "NewPassword1!"))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/member/profile-edit"))
            .andExpect(model().attributeHasFieldErrors("passwordForm", "currentPassword"));
    }

    @Test
    void validPasswordChangeRedirectsWithFlashMessage() throws Exception {
        mockMvc.perform(post("/mypage/password")
                .param("currentPassword", "Current1!")
                .param("newPassword", "NewPassword1!")
                .param("newPasswordConfirm", "NewPassword1!"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/mypage/profile"))
            .andExpect(flash().attribute("successMessage", "비밀번호를 변경했습니다."));
    }

    @Test
    void withdrawEndsSessionAndRedirectsHome() throws Exception {
        mockMvc.perform(post("/mypage/withdraw"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/"))
            .andExpect(flash().attributeExists("successMessage"));

        verify(memberService).withdraw(1L);
    }

    private MemberProfileView profile() {
        return new MemberProfileView(1L, "테스트회원", "user@cakeshop.local",
            "010-0000-0002", LocalDateTime.of(2026, 1, 1, 0, 0));
    }
}
