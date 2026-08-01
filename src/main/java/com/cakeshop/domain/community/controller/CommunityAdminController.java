package com.cakeshop.domain.community.controller;

import com.cakeshop.domain.community.dto.form.PostBlockForm;
import com.cakeshop.domain.community.dto.view.PostCategoryView;
import com.cakeshop.domain.community.service.CommunityAdminService;
import com.cakeshop.domain.community.service.CommunityService;
import com.cakeshop.global.common.paging.PageQuery;
import com.cakeshop.global.common.paging.PageRequest;
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

// /admin/community 관리·게시글 제재
@Controller
public class CommunityAdminController {

    private static final int PAGE_SIZE = 10;

    private final CommunityAdminService communityAdminService;
    private final CommunityService communityService;

    public CommunityAdminController(CommunityAdminService communityAdminService,
                                    CommunityService communityService) {
        this.communityAdminService = communityAdminService;
        this.communityService = communityService;
    }

    @GetMapping("/admin/community")
    public String list(@RequestParam(required = false) String status,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) String title,
                       @RequestParam(required = false) String writer,
                       @RequestParam(required = false) Integer page,
                       Model model) {
        String currentStatus = communityAdminService.normalizeStatus(status);
        // 활성 카테고리는 한 번만 조회해 필터 검증과 화면 출력에 함께 쓴다(고객 목록과 같은 방식).
        List<PostCategoryView> categories = communityService.getActiveCategories();
        String currentCategory = communityService.normalizeCategory(category, categories);
        model.addAttribute("categories", categories);
        model.addAttribute("currentStatus", currentStatus);
        model.addAttribute("currentCategory", currentCategory);
        model.addAttribute("currentTitle", title);
        model.addAttribute("currentWriter", writer);
        model.addAttribute("pageResult",
            communityAdminService.getPostPage(currentStatus, currentCategory, title, writer,
                new PageRequest(page, PAGE_SIZE)));
        // 페이지 링크에 검색 조건을 유지한다. 인코딩은 PageQuery가 처리한다(한글 검색어).
        model.addAttribute("extraQuery", PageQuery.of()
            .add("status", currentStatus)
            .add("category", currentCategory)
            .add("title", title)
            .add("writer", writer)
            .toQueryString());
        return "admin/community/list";
    }

    @GetMapping("/admin/community/{postId}")
    public String detail(@PathVariable long postId, Model model) {
        model.addAttribute("post", communityAdminService.getPostDetail(postId));
        model.addAttribute("blockForm", new PostBlockForm());
        return "admin/community/detail";
    }

    @PostMapping("/admin/community/{postId}/block")
    public String block(@PathVariable long postId,
                        @AuthenticationPrincipal MemberDetails admin,
                        @Valid @ModelAttribute("blockForm") PostBlockForm blockForm,
                        BindingResult bindingResult,
                        Model model,
                        RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("post", communityAdminService.getPostDetail(postId));
            return "admin/community/detail";
        }
        communityAdminService.blockPost(admin.getMemberId(), postId, blockForm);
        redirectAttributes.addFlashAttribute("successMessage", "게시글을 제재했습니다.");
        return "redirect:/admin/community/" + postId;
    }

    @PostMapping("/admin/community/{postId}/unblock")
    public String unblock(@PathVariable long postId, RedirectAttributes redirectAttributes) {
        communityAdminService.unblockPost(postId);
        redirectAttributes.addFlashAttribute("successMessage", "제재를 해제했습니다.");
        return "redirect:/admin/community/" + postId;
    }

    @PostMapping("/admin/community/{postId}/comments/{commentId}/delete")
    public String deleteComment(@PathVariable long postId,
                                @PathVariable long commentId,
                                RedirectAttributes redirectAttributes) {
        communityAdminService.deleteComment(postId, commentId);
        redirectAttributes.addFlashAttribute("successMessage", "댓글을 삭제했습니다.");
        return "redirect:/admin/community/" + postId;
    }
}
