package com.cakeshop.domain.member.controller;

import com.cakeshop.domain.member.dto.form.SignupForm;
import com.cakeshop.domain.member.error.MemberErrorCode;
import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.global.error.BusinessException;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final MemberService memberService;

    public AuthController(MemberService memberService) {
        this.memberService = memberService;
    }

    // POST /login은 Spring Security가 처리하고 컨트롤러는 화면만 반환한다.
    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/signup")
    public String signup(Model model) {
        model.addAttribute("signupForm", new SignupForm());
        return "customer/member/signup";
    }

    @PostMapping("/signup")
    public String signup(@Valid @ModelAttribute("signupForm") SignupForm form,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                memberService.signup(form);
            } catch (BusinessException e) {
                if (e.getErrorCode() == MemberErrorCode.DUPLICATE_EMAIL) {
                    // 화면에서 바로 고칠 수 있는 업무 오류는 해당 입력 필드에 돌려준다.
                    bindingResult.rejectValue("email", e.getErrorCode().code(), e.getMessage());
                } else {
                    throw e;
                }
            }
        }

        if (bindingResult.hasErrors()) {
            return "customer/member/signup";
        }

        redirectAttributes.addFlashAttribute("successMessage", "회원가입이 완료되었습니다. 로그인해 주세요.");
        return "redirect:/login";
    }
}
