package com.cakeshop.domain.member.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.member.dto.form.SignupForm;
import com.cakeshop.domain.member.error.MemberErrorCode;
import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.global.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthControllerTests {

    @Mock
    private MemberService memberService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(memberService)).build();
    }

    @Test
    void loginPageRenders() throws Exception {
        mockMvc.perform(get("/login"))
            .andExpect(status().isOk())
            .andExpect(view().name("auth/login"));
    }

    @Test
    void signupPageProvidesEmptyForm() throws Exception {
        mockMvc.perform(get("/signup"))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/member/signup"))
            .andExpect(model().attributeExists("signupForm"));
    }

    @Test
    void invalidSignupRendersSameFormWithoutCallingService() throws Exception {
        mockMvc.perform(post("/signup").param("email", ""))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/member/signup"))
            .andExpect(model().attributeHasFieldErrors("signupForm", "email", "password", "nickname", "phone"));

        verify(memberService, never()).signup(any(SignupForm.class));
    }

    @Test
    void mismatchedPasswordConfirmIsRejected() throws Exception {
        mockMvc.perform(validSignupRequest().param("passwordConfirm", "Different1!"))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/member/signup"))
            .andExpect(model().attributeHasFieldErrors("signupForm", "passwordConfirmed"));

        verify(memberService, never()).signup(any(SignupForm.class));
    }

    @Test
    void duplicateEmailReturnsFieldError() throws Exception {
        doThrow(new BusinessException(MemberErrorCode.DUPLICATE_EMAIL))
            .when(memberService).signup(any(SignupForm.class));

        mockMvc.perform(validSignupRequest())
            .andExpect(status().isOk())
            .andExpect(view().name("customer/member/signup"))
            .andExpect(model().attributeHasFieldErrors("signupForm", "email"));
    }

    @Test
    void validSignupRedirectsToLoginWithFlashMessage() throws Exception {
        mockMvc.perform(validSignupRequest())
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"))
            .andExpect(flash().attribute("successMessage", "회원가입이 완료되었습니다. 로그인해 주세요."));

        verify(memberService).signup(any(SignupForm.class));
    }

    private MockHttpServletRequestBuilder validSignupRequest() {
        return post("/signup")
            .param("email", "new@cakeshop.local")
            .param("password", "Password1!")
            .param("passwordConfirm", "Password1!")
            .param("nickname", "신규회원")
            .param("phone", "010-1234-5678")
            .param("termsService", "true")
            .param("termsPrivacy", "true");
    }
}
