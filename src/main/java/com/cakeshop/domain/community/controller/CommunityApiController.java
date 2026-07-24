package com.cakeshop.domain.community.controller;

import com.cakeshop.domain.community.dto.view.LikeResultView;
import com.cakeshop.domain.community.dto.view.PostSliceView;
import com.cakeshop.domain.community.service.CommunityService;
import com.cakeshop.global.security.MemberDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 댓글·좋아요 — 동적 처리가 필요할 때만 사용
@RestController
public class CommunityApiController {

    private final CommunityService communityService;

    public CommunityApiController(CommunityService communityService) {
        this.communityService = communityService;
    }

    // 무한스크롤 목록 조회. cursor 미지정이면 최신 글부터 내려준다.
    @GetMapping("/community/api/posts")
    public PostSliceView posts(@RequestParam(required = false) String category,
                               @RequestParam(required = false) Long cursor,
                               @RequestParam(required = false) Integer size) {
        return communityService.getPostSlice(category, cursor, size);
    }

    // 좋아요 토글 (로그인 필수 — 화면은 비로그인 버튼을 비활성화한다)
    @PostMapping("/community/api/posts/{postId}/like")
    public LikeResultView toggleLike(@PathVariable long postId,
                                     @AuthenticationPrincipal MemberDetails member) {
        return communityService.toggleLike(member.getMemberId(), postId);
    }
}
