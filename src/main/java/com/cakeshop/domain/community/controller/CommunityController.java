package com.cakeshop.domain.community.controller;

import com.cakeshop.domain.community.dto.form.CommentCreateForm;
import com.cakeshop.domain.community.dto.form.PostCreateForm;
import com.cakeshop.domain.community.dto.form.PostReportForm;
import com.cakeshop.domain.community.entity.PostCategory;
import com.cakeshop.domain.community.service.CommunityService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.security.MemberDetails;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// /community 목록·상세·작성 (GET은 공개, 쓰기는 로그인 회원)
@Controller
public class CommunityController {

    // 목록 한 페이지 크기. 페이지 번호·무한스크롤 비교가 목적이라 두 방식 모두 10건으로 맞춘다.
    private static final int PAGE_SIZE = 10;

    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    // 페이지 번호 방식 목록 (기본)
    @GetMapping("/community")
    public String list(@RequestParam(required = false) String category,
                       @RequestParam(required = false) Integer page,
                       Model model) {
        // 활성 카테고리는 한 번만 조회해 필터 검증과 화면 출력에 함께 쓴다(같은 SQL 2회 실행 방지).
        List<PostCategory> categories = communityService.getActiveCategories();
        String currentCategory = communityService.normalizeCategory(category, categories);
        if (category != null && !category.isBlank() && currentCategory == null) {
            model.addAttribute("errorMessage", "잘못된 카테고리 양식입니다.");
        }
        model.addAttribute("categories", categories);
        model.addAttribute("currentCategory", currentCategory);
        model.addAttribute("pageResult",
            communityService.getPostPage(currentCategory, new PageRequest(page, PAGE_SIZE)));
        return "customer/community/list";
    }

    // 무한스크롤 방식 목록 — 첫 화면은 빈 껍데기만 렌더하고 데이터는 /community/api/posts 로 가져온다.
    @GetMapping("/community/scroll")
    public String scrollList(@RequestParam(required = false) String category, Model model) {
        model.addAttribute("categories", communityService.getActiveCategories());
        model.addAttribute("currentCategory", communityService.normalizeCategory(category));
        return "customer/community/list-scroll";
    }

    // 상세 경로는 SecurityConfig의 "/community/{id:\\d+}" 공개 규칙에 맞춰 숫자 식별자만 받는다.
    @GetMapping("/community/{postId}")
    public String detail(@PathVariable long postId,
                         @AuthenticationPrincipal MemberDetails member,
                         Model model) {
        model.addAttribute("post",
            communityService.getPostDetail(postId, memberIdOf(member), true));
        model.addAttribute("commentForm", new CommentCreateForm());
        model.addAttribute("reportForm", new PostReportForm());
        model.addAttribute("loggedIn", member != null);
        return "customer/community/detail";
    }

    @GetMapping("/community/new")
    public String createForm(Model model) {
        model.addAttribute("categories", communityService.getActiveCategories());
        model.addAttribute("form", new PostCreateForm());
        return "customer/community/form";
    }

    @PostMapping("/community")
    public String create(@AuthenticationPrincipal MemberDetails member,
                         @Valid @ModelAttribute("form") PostCreateForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", communityService.getActiveCategories());
            return "customer/community/form";
        }
        long postId = communityService.createPost(member.getMemberId(), form);
        redirectAttributes.addFlashAttribute("successMessage", "게시글이 등록되었습니다.");
        return "redirect:/community/" + postId;
    }

    @GetMapping("/community/{postId}/edit")
    public String editForm(@PathVariable long postId,
                           @AuthenticationPrincipal MemberDetails member,
                           Model model) {
        model.addAttribute("categories", communityService.getActiveCategories());
        model.addAttribute("form", communityService.getPostForEdit(member.getMemberId(), postId));
        model.addAttribute("editing", true);
        model.addAttribute("postId", postId);
        return "customer/community/form";
    }

    @PostMapping("/community/{postId}/edit")
    public String update(@PathVariable long postId,
                         @AuthenticationPrincipal MemberDetails member,
                         @Valid @ModelAttribute("form") PostCreateForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", communityService.getActiveCategories());
            model.addAttribute("editing", true);
            model.addAttribute("postId", postId);
            return "customer/community/form";
        }
        communityService.updatePost(member.getMemberId(), postId, form);
        redirectAttributes.addFlashAttribute("successMessage", "게시글이 수정되었습니다.");
        return "redirect:/community/" + postId;
    }

    @PostMapping("/community/{postId}/delete")
    public String delete(@PathVariable long postId,
                         @AuthenticationPrincipal MemberDetails member,
                         RedirectAttributes redirectAttributes) {
        communityService.deletePost(member.getMemberId(), postId);
        redirectAttributes.addFlashAttribute("successMessage", "게시글이 삭제되었습니다.");
        return "redirect:/community";
    }

    @PostMapping("/community/{postId}/comments/{commentId}/delete")
    public String deleteComment(@PathVariable long postId,
                                @PathVariable long commentId,
                                @AuthenticationPrincipal MemberDetails member,
                                RedirectAttributes redirectAttributes) {
        communityService.deleteComment(member.getMemberId(), postId, commentId);
        redirectAttributes.addFlashAttribute("successMessage", "댓글이 삭제되었습니다.");
        return "redirect:/community/" + postId;
    }

    // 신고는 상세 화면의 접이식 소형 폼이라, 실패도 재렌더 대신 플래시로 알리고 상세로 되돌린다.
    @PostMapping("/community/{postId}/report")
    public String report(@PathVariable long postId,
                         @AuthenticationPrincipal MemberDetails member,
                         @Valid @ModelAttribute("reportForm") PostReportForm reportForm,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                bindingResult.getFieldErrors().get(0).getDefaultMessage());
            return "redirect:/community/" + postId;
        }
        try {
            communityService.reportPost(member.getMemberId(), postId, reportForm);
            redirectAttributes.addFlashAttribute("successMessage", "신고가 접수되었습니다.");
        } catch (BusinessException e) {
            // 중복 신고·본인 글 신고는 화면에서 바로 안내한다.
            redirectAttributes.addFlashAttribute("errorMessage", e.getErrorCode().message());
        }
        return "redirect:/community/" + postId;
    }

    @PostMapping("/community/{postId}/comments")
    public String addComment(@PathVariable long postId,
                             @AuthenticationPrincipal MemberDetails member,
                             @Valid @ModelAttribute("commentForm") CommentCreateForm commentForm,
                             BindingResult bindingResult,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            // 검증 실패는 상세 화면을 재렌더한다. 조회수는 최초 진입이 아니므로 올리지 않는다.
            model.addAttribute("post",
                communityService.getPostDetail(postId, member.getMemberId(), false));
            model.addAttribute("reportForm", new PostReportForm());
            model.addAttribute("loggedIn", true);
            return "customer/community/detail";
        }
        communityService.addComment(member.getMemberId(), postId, commentForm);
        redirectAttributes.addFlashAttribute("successMessage", "댓글이 등록되었습니다.");
        return "redirect:/community/" + postId;
    }

    private Long memberIdOf(MemberDetails member) {
        return member == null ? null : member.getMemberId();
    }
}
