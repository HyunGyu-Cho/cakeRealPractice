package com.cakeshop.domain.member.controller;

import com.cakeshop.domain.member.dto.form.AdminMemberSearchForm;
import com.cakeshop.domain.member.dto.form.MemberSuspendForm;
import com.cakeshop.domain.member.entity.MemberStatus;
import com.cakeshop.domain.member.service.MemberAdminService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.error.BusinessException;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

/** 관리자 회원 관리 — 목록·검색, 상세, 이용 제한·해제. */
@Controller
@RequestMapping("/admin/members")
public class MemberAdminController {

    private static final int PAGE_SIZE = 10;

    private final MemberAdminService memberAdminService;

    public MemberAdminController(MemberAdminService memberAdminService) {
        this.memberAdminService = memberAdminService;
    }

    @GetMapping
    public String list(@ModelAttribute("search") AdminMemberSearchForm search, Model model) {
        model.addAttribute("pageResult",
            memberAdminService.getMemberPage(search, new PageRequest(search.getPage(), PAGE_SIZE)));
        model.addAttribute("memberStatuses", MemberStatus.values());
        model.addAttribute("extraQuery", extraQuery(search));
        return "admin/member/list";
    }

    @GetMapping("/{memberId}")
    public String detail(@PathVariable Long memberId, Model model) {
        model.addAttribute("member", memberAdminService.getMemberDetail(memberId));
        model.addAttribute("suspendForm", new MemberSuspendForm());
        return "admin/member/detail";
    }

    /** 사유가 비면 리다이렉트하지 않고 상세를 재렌더해 입력값과 필드 오류를 유지한다. */
    @PostMapping("/{memberId}/suspend")
    public String suspend(@PathVariable Long memberId,
                          @Valid @ModelAttribute("suspendForm") MemberSuspendForm form,
                          BindingResult bindingResult,
                          Model model,
                          RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("member", memberAdminService.getMemberDetail(memberId));
            return "admin/member/detail";
        }
        try {
            memberAdminService.suspend(memberId, form.getReason());
            redirectAttributes.addFlashAttribute("successMessage", "회원 이용을 제한했습니다.");
        } catch (BusinessException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/admin/members/" + memberId;
    }

    @PostMapping("/{memberId}/unsuspend")
    public String unsuspend(@PathVariable Long memberId, RedirectAttributes redirectAttributes) {
        try {
            memberAdminService.unsuspend(memberId);
            redirectAttributes.addFlashAttribute("successMessage", "이용 제한을 해제했습니다.");
        } catch (BusinessException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/admin/members/" + memberId;
    }

    /** 페이지 이동 링크가 검색 조건을 잃지 않도록 쿼리스트링으로 만들어 둔다. */
    private String extraQuery(AdminMemberSearchForm search) {
        StringBuilder query = new StringBuilder();
        if (search.getNormalizedName() != null) {
            query.append("&name=").append(encode(search.getNormalizedName()));
        }
        if (search.getNormalizedEmail() != null) {
            query.append("&email=").append(encode(search.getNormalizedEmail()));
        }
        if (search.getNormalizedStatus() != null) {
            query.append("&status=").append(search.getNormalizedStatus());
        }
        return query.toString();
    }

    private String encode(String value) {
        return UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8);
    }
}
