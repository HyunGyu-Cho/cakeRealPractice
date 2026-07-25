package com.cakeshop.domain.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.community.dto.form.PostBlockForm;
import com.cakeshop.domain.community.dto.view.AdminPostDetailRow;
import com.cakeshop.domain.community.entity.Comment;
import com.cakeshop.domain.community.entity.CommentStatus;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.mapper.CommunityMapper;
import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommunityAdminServiceTests {

    @Mock
    private CommunityMapper communityMapper;

    @Mock
    private MemberService memberService;

    private CommunityAdminService communityAdminService;

    @BeforeEach
    void setUp() {
        communityAdminService = new CommunityAdminService(communityMapper, memberService);
    }

    @Test
    void blockPostAllowsOnlyActivePostAndTrimsReason() {
        when(communityMapper.findPostDetailForAdmin(10L))
            .thenReturn(Optional.of(post(10L, PostStatus.ACTIVE)));
        PostBlockForm form = new PostBlockForm();
        form.setReason(" 광고 게시물 ");

        communityAdminService.blockPost(99L, 10L, form);

        verify(communityMapper).blockPost(10L, "광고 게시물", 99L);
    }

    @Test
    void blockPostRejectsAlreadyBlockedPost() {
        when(communityMapper.findPostDetailForAdmin(10L))
            .thenReturn(Optional.of(post(10L, PostStatus.BLOCKED)));
        PostBlockForm form = new PostBlockForm();
        form.setReason("사유");

        assertCommunityError(
            () -> communityAdminService.blockPost(99L, 10L, form),
            CommunityErrorCode.INVALID_STATUS_CHANGE);

        verify(communityMapper, never()).blockPost(10L, "사유", 99L);
    }

    @Test
    void unblockPostAllowsOnlyBlockedPost() {
        when(communityMapper.findPostDetailForAdmin(10L))
            .thenReturn(Optional.of(post(10L, PostStatus.ACTIVE)));

        assertCommunityError(
            () -> communityAdminService.unblockPost(10L),
            CommunityErrorCode.INVALID_STATUS_CHANGE);

        verify(communityMapper, never()).unblockPost(10L);
    }

    @Test
    void deleteCommentRejectsCommentFromDifferentPost() {
        when(communityMapper.findPostDetailForAdmin(10L))
            .thenReturn(Optional.of(post(10L, PostStatus.ACTIVE)));
        Comment comment = new Comment();
        comment.setId(20L);
        comment.setPostId(11L);
        comment.setStatus(CommentStatus.ACTIVE);
        when(communityMapper.findCommentById(20L)).thenReturn(Optional.of(comment));

        assertCommunityError(
            () -> communityAdminService.deleteComment(10L, 20L),
            CommunityErrorCode.COMMENT_NOT_FOUND);

        verify(communityMapper, never()).softDeleteComment(20L);
    }

    private void assertCommunityError(Runnable action, CommunityErrorCode expected) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(expected));
    }

    private AdminPostDetailRow post(long id, PostStatus status) {
        AdminPostDetailRow row = new AdminPostDetailRow();
        row.setId(id);
        row.setMemberId(1L);
        row.setCategoryName("공지");
        row.setTitle("제목");
        row.setContent("내용");
        row.setStatus(status);
        row.setCreatedAt(LocalDateTime.of(2026, 7, 25, 12, 0));
        return row;
    }
}
