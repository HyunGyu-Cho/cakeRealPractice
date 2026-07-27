package com.cakeshop.domain.member.controller;

import com.cakeshop.domain.member.dto.form.PasswordForm;
import com.cakeshop.domain.member.dto.form.ProfileUpdateForm;
import com.cakeshop.domain.member.dto.view.MemberProfileView;
import com.cakeshop.domain.member.error.MemberErrorCode;
import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.domain.order.service.OrderService;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.security.MemberDetails;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class MyPageController {

    private final MemberService memberService;
    private final OrderService orderService;

    public MyPageController(MemberService memberService, OrderService orderService) {
        this.memberService = memberService;
        this.orderService = orderService;
    }

    @GetMapping("/mypage")
    public String myPage(@AuthenticationPrincipal MemberDetails member, Model model) {
        model.addAttribute("profile", memberService.getProfile(member.getMemberId()));
        model.addAttribute("orders", orderService.getMyOrderOverview(member.getMemberId()));
        return "customer/member/mypage";
    }

    @GetMapping("/mypage/profile")
    public String profile(@AuthenticationPrincipal MemberDetails member, Model model) {
        MemberProfileView profile = memberService.getProfile(member.getMemberId());
        model.addAttribute("profile", profile);
        model.addAttribute("profileForm", ProfileUpdateForm.from(profile));
        model.addAttribute("passwordForm", new PasswordForm());
        return "customer/member/profile-edit";
    }

    @PostMapping("/mypage/profile")
    public String updateProfile(@AuthenticationPrincipal MemberDetails member,
                                @Valid @ModelAttribute("profileForm") ProfileUpdateForm form,
                                BindingResult bindingResult,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            // PRG 전 검증 실패는 사용자가 입력한 form을 그대로 재렌더한다(다른 폼은 초기값으로 채운다).
            model.addAttribute("profile", memberService.getProfile(member.getMemberId()));
            model.addAttribute("passwordForm", new PasswordForm());
            return "customer/member/profile-edit";
        }

        memberService.updateProfile(member.getMemberId(), form);
        redirectAttributes.addFlashAttribute("successMessage", "회원 정보를 수정했습니다.");
        return "redirect:/mypage/profile";
    }

    @PostMapping("/mypage/password")
    public String changePassword(@AuthenticationPrincipal MemberDetails member,
                                 @Valid @ModelAttribute("passwordForm") PasswordForm form,
                                 BindingResult bindingResult,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                memberService.changePassword(member.getMemberId(), form);
            } catch (BusinessException e) {
                if (e.getErrorCode() == MemberErrorCode.PASSWORD_MISMATCH) {
                    // 화면에서 바로 고칠 수 있는 업무 오류는 해당 입력 필드에 돌려준다.
                    bindingResult.rejectValue("currentPassword", e.getErrorCode().code(), e.getMessage());
                } else {
                    throw e;
                }
            }
        }

        if (bindingResult.hasErrors()) {
            MemberProfileView profile = memberService.getProfile(member.getMemberId());
            model.addAttribute("profile", profile);
            model.addAttribute("profileForm", ProfileUpdateForm.from(profile));
            return "customer/member/profile-edit";
        }

        redirectAttributes.addFlashAttribute("successMessage", "비밀번호를 변경했습니다.");
        return "redirect:/mypage/profile";
    }

    @PostMapping("/mypage/withdraw")
    public String withdraw(@AuthenticationPrincipal MemberDetails member,
                           HttpServletRequest request,
                           RedirectAttributes redirectAttributes) throws ServletException {
        memberService.withdraw(member.getMemberId());
        // 탈퇴 확정 후 세션을 종료해야 이후 요청이 탈퇴 계정으로 처리되지 않는다.
        request.logout();
        redirectAttributes.addFlashAttribute("successMessage", "탈퇴가 완료되었습니다. 그동안 이용해 주셔서 감사합니다.");
        return "redirect:/";
    }
}
