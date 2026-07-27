package com.cakeshop.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.domain.notification.entity.NotificationType;
import com.cakeshop.domain.notification.service.NotificationCommand;
import com.cakeshop.domain.notification.service.NotificationService;
import com.cakeshop.domain.review.dto.form.AdminReviewSearchForm;
import com.cakeshop.domain.review.dto.form.ReviewReplyForm;
import com.cakeshop.domain.review.dto.view.AdminReviewListView;
import com.cakeshop.domain.review.dto.view.AdminReviewRow;
import com.cakeshop.domain.review.entity.Review;
import com.cakeshop.domain.review.entity.ReviewReply;
import com.cakeshop.domain.review.entity.ReviewStatus;
import com.cakeshop.domain.review.error.ReviewErrorCode;
import com.cakeshop.domain.review.mapper.ReviewMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewAdminServiceTests {

    private static final Long REVIEW_ID = 100L;
    private static final Long PRODUCT_ID = 8L;
    private static final Long ADMIN_ID = 1L;

    @Mock private ReviewMapper reviewMapper;
    @Mock private ReviewService reviewService;
    @Mock private MemberService memberService;
    @Mock private NotificationService notificationService;

    private ReviewAdminService reviewAdminService;

    @BeforeEach
    void setUp() {
        reviewAdminService = new ReviewAdminService(
            reviewMapper, reviewService, memberService, notificationService);
    }

    @Test
    void hidingReviewRefreshesProductStats() {
        when(reviewMapper.findById(REVIEW_ID)).thenReturn(Optional.of(review("VISIBLE")));
        when(reviewMapper.updateStatus(REVIEW_ID, "VISIBLE", "HIDDEN")).thenReturn(1);

        reviewAdminService.changeStatus(REVIEW_ID, ReviewStatus.HIDDEN);

        // 숨긴 후기는 집계에서도 빠져야 하므로 같은 트랜잭션에서 재계산한다
        verify(reviewService).refreshStats(PRODUCT_ID);
    }

    @Test
    void sameStatusIsRejectedAsInvalidTransition() {
        when(reviewMapper.findById(REVIEW_ID)).thenReturn(Optional.of(review("VISIBLE")));

        assertThatThrownBy(() -> reviewAdminService.changeStatus(REVIEW_ID, ReviewStatus.VISIBLE))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ReviewErrorCode.INVALID_STATUS_TRANSITION);
        verify(reviewMapper, never()).updateStatus(anyLong(), anyString(), anyString());
    }

    @Test
    void statusChangeLosingTheRaceDoesNotRefreshStats() {
        when(reviewMapper.findById(REVIEW_ID)).thenReturn(Optional.of(review("VISIBLE")));
        // 그 사이 다른 관리자가 먼저 바꿨다 — 조건부 UPDATE 영향 행 0
        when(reviewMapper.updateStatus(REVIEW_ID, "VISIBLE", "HIDDEN")).thenReturn(0);

        assertThatThrownBy(() -> reviewAdminService.changeStatus(REVIEW_ID, ReviewStatus.HIDDEN))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ReviewErrorCode.INVALID_STATUS_TRANSITION);
        verify(reviewService, never()).refreshStats(anyLong());
    }

    @Test
    void replyIsInsertedWhenAbsentAndUpdatedWhenPresent() {
        when(reviewMapper.findById(REVIEW_ID)).thenReturn(Optional.of(review("VISIBLE")));
        when(reviewMapper.findReplyByReviewId(REVIEW_ID)).thenReturn(Optional.empty());

        reviewAdminService.saveReply(REVIEW_ID, ADMIN_ID, replyForm("감사합니다!"));
        verify(reviewMapper).insertReply(any(ReviewReply.class));

        // 후기당 답글 1개라 두 번째 저장은 수정이 된다(uk_review_replies_review)
        when(reviewMapper.findReplyByReviewId(REVIEW_ID)).thenReturn(Optional.of(new ReviewReply()));
        reviewAdminService.saveReply(REVIEW_ID, ADMIN_ID, replyForm("다시 감사합니다!"));
        verify(reviewMapper).updateReply(REVIEW_ID, ADMIN_ID, "다시 감사합니다!");
    }

    @Test
    void firstReplyNotifiesTheAuthorAndEditsDoNot() {
        when(reviewMapper.findById(REVIEW_ID)).thenReturn(Optional.of(review("VISIBLE")));
        when(reviewMapper.findReplyByReviewId(REVIEW_ID)).thenReturn(Optional.empty());

        reviewAdminService.saveReply(REVIEW_ID, ADMIN_ID, replyForm("감사합니다!"));

        ArgumentCaptor<NotificationCommand> captor =
            ArgumentCaptor.forClass(NotificationCommand.class);
        verify(notificationService).notify(captor.capture());
        NotificationCommand command = captor.getValue();
        assertThat(command.receiverId()).isEqualTo(7L);          // 관리자가 아니라 후기 작성자
        assertThat(command.type()).isEqualTo(NotificationType.REVIEW_REPLY);
        assertThat(command.targetUrl()).isEqualTo("/reviews");
        assertThat(command.content()).contains("감사합니다!");

        // 답글은 후기당 1개라 수정마다 발행하면 같은 답글로 작성자를 반복해서 깨운다
        when(reviewMapper.findReplyByReviewId(REVIEW_ID)).thenReturn(Optional.of(new ReviewReply()));
        reviewAdminService.saveReply(REVIEW_ID, ADMIN_ID, replyForm("수정한 답글입니다."));

        verify(notificationService, times(1)).notify(any());
    }

    @Test
    void notificationBodyTruncatesLongReplies() {
        when(reviewMapper.findById(REVIEW_ID)).thenReturn(Optional.of(review("VISIBLE")));
        when(reviewMapper.findReplyByReviewId(REVIEW_ID)).thenReturn(Optional.empty());
        String longReply = "가".repeat(60);

        reviewAdminService.saveReply(REVIEW_ID, ADMIN_ID, replyForm(longReply));

        ArgumentCaptor<NotificationCommand> captor =
            ArgumentCaptor.forClass(NotificationCommand.class);
        verify(notificationService).notify(captor.capture());
        assertThat(captor.getValue().content())
            .endsWith("가".repeat(40) + "...")
            .doesNotContain("가".repeat(41));
    }

    @Test
    void replyToMissingReviewNotifiesNobody() {
        when(reviewMapper.findById(REVIEW_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewAdminService.saveReply(REVIEW_ID, ADMIN_ID, replyForm("답글")))
            .isInstanceOf(BusinessException.class);
        verify(notificationService, never()).notify(any());
    }

    @Test
    void replyToMissingReviewIsRejected() {
        when(reviewMapper.findById(REVIEW_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewAdminService.saveReply(REVIEW_ID, ADMIN_ID, replyForm("답글")))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ReviewErrorCode.NOT_FOUND);
        verify(reviewMapper, never()).insertReply(any());
    }

    @Test
    void listFillsNicknamesThroughMemberContractInsteadOfJoin() {
        AdminReviewSearchForm cond = new AdminReviewSearchForm();
        when(reviewMapper.countAdminReviews(cond)).thenReturn(1L);
        when(reviewMapper.findAdminReviewPage(cond, 10, 0)).thenReturn(List.of(row()));
        when(memberService.getNicknameMap(any())).thenReturn(Map.of(7L, "단골손님"));

        PageResult<AdminReviewListView> page =
            reviewAdminService.getReviewPage(cond, new PageRequest(1, 10));

        AdminReviewListView view = page.getContent().getFirst();
        assertThat(view.writerNickname()).isEqualTo("단골손님");
        assertThat(view.hidden()).isFalse();
        assertThat(view.statusLabel()).isEqualTo("공개");
    }

    private Review review(String status) {
        Review review = new Review();
        review.setId(REVIEW_ID);
        review.setProductId(PRODUCT_ID);
        review.setMemberId(7L);
        review.setStatus(status);
        return review;
    }

    private AdminReviewRow row() {
        return new AdminReviewRow(REVIEW_ID, 7L, PRODUCT_ID, "초코 가나슈 케이크", 5,
            5, null, null, "맛있어요. 또 시킬게요.", "VISIBLE",
            LocalDateTime.of(2026, 7, 26, 10, 0), null, null);
    }

    private ReviewReplyForm replyForm(String content) {
        ReviewReplyForm form = new ReviewReplyForm();
        form.setContent(content);
        return form;
    }
}
