package com.cakeshop.domain.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.community.dto.form.CommentCreateForm;
import com.cakeshop.domain.community.dto.form.PostCreateForm;
import com.cakeshop.domain.community.dto.form.PostReportForm;
import com.cakeshop.domain.community.dto.view.LikeResultView;
import com.cakeshop.domain.community.dto.view.PostDetailRow;
import com.cakeshop.domain.community.dto.view.PostSummaryRow;
import com.cakeshop.domain.community.dto.view.PostSummaryView;
import com.cakeshop.domain.community.entity.Comment;
import com.cakeshop.domain.community.entity.CommentStatus;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.mapper.CommunityMapper;
import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommunityServiceTests {

    @Mock
    private CommunityMapper communityMapper;

    @Mock
    private MemberService memberService;

    private CommunityService communityService;

    @BeforeEach
    void setUp() {
        communityService = new CommunityService(communityMapper, memberService);
    }

    @Test
    void updatePostRejectsMemberWhoIsNotAuthor() {
        when(communityMapper.findPostDetail(10L)).thenReturn(Optional.of(post(10L, 1L, PostStatus.ACTIVE)));

        assertCommunityError(
            () -> communityService.updatePost(2L, 10L, postForm()),
            CommunityErrorCode.NOT_AUTHOR);

        verify(communityMapper, never()).findCategoryByCode(any());
        verify(communityMapper, never()).updatePost(any(Long.class), any(Long.class), any(), any());
    }

    @Test
    void addCommentRejectsReplyToAnotherReply() {
        when(communityMapper.findPostDetail(10L)).thenReturn(Optional.of(post(10L, 1L, PostStatus.ACTIVE)));
        Comment parent = comment(20L, 10L, 1L, CommentStatus.ACTIVE);
        parent.setParentCommentId(19L);
        when(communityMapper.findCommentById(20L)).thenReturn(Optional.of(parent));
        CommentCreateForm form = new CommentCreateForm();
        form.setParentCommentId(20L);
        form.setContent(" 답글 ");

        assertCommunityError(
            () -> communityService.addComment(2L, 10L, form),
            CommunityErrorCode.PARENT_COMMENT_NOT_FOUND);

        verify(communityMapper, never()).insertComment(any(Comment.class));
    }

    @Test
    void reportPostRejectsOwnPost() {
        when(communityMapper.findPostDetail(10L)).thenReturn(Optional.of(post(10L, 1L, PostStatus.ACTIVE)));
        PostReportForm form = new PostReportForm();
        form.setReason("신고 사유");

        assertCommunityError(
            () -> communityService.reportPost(1L, 10L, form),
            CommunityErrorCode.CANNOT_REPORT_OWN_POST);

        verify(communityMapper, never()).insertReport(any(Long.class), any(Long.class), any());
    }

    @Test
    void reportPostRejectsDuplicateReport() {
        when(communityMapper.findPostDetail(10L)).thenReturn(Optional.of(post(10L, 1L, PostStatus.ACTIVE)));
        when(communityMapper.existsReport(10L, 2L)).thenReturn(true);
        PostReportForm form = new PostReportForm();
        form.setReason("신고 사유");

        assertCommunityError(
            () -> communityService.reportPost(2L, 10L, form),
            CommunityErrorCode.ALREADY_REPORTED);

        verify(communityMapper, never()).insertReport(any(Long.class), any(Long.class), any());
    }

    @Test
    void toggleLikeCreatesLikeAndIncrementsDerivedCount() {
        when(communityMapper.findPostDetail(10L)).thenReturn(Optional.of(post(10L, 1L, PostStatus.ACTIVE)));
        when(communityMapper.existsPostLike(10L, 2L)).thenReturn(false);
        when(communityMapper.findLikeCount(10L)).thenReturn(3L);

        LikeResultView result = communityService.toggleLike(2L, 10L);

        assertThat(result.liked()).isTrue();
        assertThat(result.likeCount()).isEqualTo(3L);
        verify(communityMapper).insertPostLike(10L, 2L);
        verify(communityMapper).addLikeCount(10L, 1);
    }

    @Test
    void blockedPostIsForbiddenAndDeletedPostIsHidden() {
        when(communityMapper.findPostDetail(10L))
            .thenReturn(Optional.of(post(10L, 1L, PostStatus.BLOCKED)));
        when(communityMapper.findPostDetail(11L))
            .thenReturn(Optional.of(post(11L, 1L, PostStatus.DELETED)));

        assertCommunityError(
            () -> communityService.getPostDetail(10L, null, true),
            CommunityErrorCode.BLOCKED_POST);
        assertCommunityError(
            () -> communityService.getPostDetail(11L, null, true),
            CommunityErrorCode.POST_NOT_FOUND);

        verify(communityMapper, never()).increaseViewCount(any(Long.class));
    }

    private void assertCommunityError(Runnable action, CommunityErrorCode expected) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(expected));
    }

    @Test
    void popularPostsAreCappedBySliceMaxSize() {
        when(communityMapper.findPopularPosts(30)).thenReturn(List.of(summaryRow(1L, 12L)));
        when(memberService.getNicknameMap(Set.of(2L))).thenReturn(Map.of(2L, "케이크러버"));

        List<PostSummaryView> posts = communityService.getPopularPosts(999);

        assertThat(posts).hasSize(1);
        assertThat(posts.get(0).nickname()).isEqualTo("케이크러버");
        assertThat(posts.get(0).likeCount()).isEqualTo(12L);
    }

    @Test
    void popularPostsSkipQueryWhenNothingRequested() {
        assertThat(communityService.getPopularPosts(0)).isEmpty();

        verify(communityMapper, never()).findPopularPosts(anyInt());
    }

    private PostSummaryRow summaryRow(long id, long likeCount) {
        PostSummaryRow row = new PostSummaryRow();
        row.setId(id);
        row.setMemberId(2L);
        row.setCategoryCode("FREE");
        row.setCategoryName("자유");
        row.setTitle("제목");
        row.setLikeCount(likeCount);
        row.setCommentCount(3L);
        row.setCreatedAt(LocalDateTime.of(2026, 7, 25, 12, 0));
        return row;
    }

    private PostCreateForm postForm() {
        PostCreateForm form = new PostCreateForm();
        form.setCategoryCode("NOTICE");
        form.setTitle(" 제목 ");
        form.setContent(" 내용 ");
        return form;
    }

    private PostDetailRow post(long id, long memberId, PostStatus status) {
        PostDetailRow row = new PostDetailRow();
        row.setId(id);
        row.setMemberId(memberId);
        row.setCategoryCode("NOTICE");
        row.setCategoryName("공지");
        row.setTitle("제목");
        row.setContent("내용");
        row.setStatus(status);
        row.setCreatedAt(LocalDateTime.of(2026, 7, 25, 12, 0));
        return row;
    }

    private Comment comment(long id, long postId, long memberId, CommentStatus status) {
        Comment comment = new Comment();
        comment.setId(id);
        comment.setPostId(postId);
        comment.setMemberId(memberId);
        comment.setStatus(status);
        return comment;
    }
}
