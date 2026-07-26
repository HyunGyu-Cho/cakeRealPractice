package com.cakeshop.domain.review.service;

import com.cakeshop.domain.member.service.MemberService;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 후기 관리 — 목록·검색, 숨김·복구, 후기당 답글 1개. */
@Service
public class ReviewAdminService {

    private final ReviewMapper reviewMapper;
    private final ReviewService reviewService;
    private final MemberService memberService;

    public ReviewAdminService(ReviewMapper reviewMapper, ReviewService reviewService,
                              MemberService memberService) {
        this.reviewMapper = reviewMapper;
        this.reviewService = reviewService;
        this.memberService = memberService;
    }

    @Transactional(readOnly = true)
    public PageResult<AdminReviewListView> getReviewPage(AdminReviewSearchForm cond,
                                                         PageRequest pageRequest) {
        long total = reviewMapper.countAdminReviews(cond);
        if (total == 0) {
            return new PageResult<>(List.of(), pageRequest, 0);
        }
        List<AdminReviewRow> rows = reviewMapper.findAdminReviewPage(
            cond, pageRequest.getSize(), pageRequest.getOffset());
        // 작성자 닉네임은 members를 JOIN하지 않고 member 도메인의 공개 계약으로 일괄 조회한다.
        Map<Long, String> nicknames = memberService.getNicknameMap(
            rows.stream().map(AdminReviewRow::memberId).collect(Collectors.toSet()));
        List<AdminReviewListView> content = rows.stream().map(row -> {
            ReviewStatus status = ReviewStatus.valueOf(row.status());
            return new AdminReviewListView(
                row.id(), nicknames.getOrDefault(row.memberId(), "알 수 없음"),
                row.productId(), row.productName(), row.overallRating(), row.content(),
                status.name(), status.label(), status == ReviewStatus.HIDDEN,
                row.createdAt(), row.replyContent(), row.replyCreatedAt());
        }).toList();
        return new PageResult<>(content, pageRequest, total);
    }

    /**
     * 숨김·복구. 전이는 {@link ReviewStatus}가 소유하고 DB에는 현재 상태를 건 조건부 UPDATE로만 반영한다.
     * 숨긴 후기는 집계에서 빠져야 하므로 같은 트랜잭션에서 상품 평점을 다시 계산한다.
     */
    @Transactional
    public void changeStatus(Long reviewId, ReviewStatus next) {
        Review review = reviewMapper.findById(reviewId)
            .orElseThrow(() -> new BusinessException(ReviewErrorCode.NOT_FOUND));
        ReviewStatus current = ReviewStatus.valueOf(review.getStatus());
        if (!current.canTransitionTo(next)
            || reviewMapper.updateStatus(reviewId, current.name(), next.name()) != 1) {
            throw new BusinessException(ReviewErrorCode.INVALID_STATUS_TRANSITION);
        }
        reviewService.refreshStats(review.getProductId());
    }

    /** 답글 등록·수정. 후기당 1개라 이미 있으면 내용을 갈아끼운다(uk_review_replies_review). */
    @Transactional
    public void saveReply(Long reviewId, Long adminId, ReviewReplyForm form) {
        if (reviewMapper.findById(reviewId).isEmpty()) {
            throw new BusinessException(ReviewErrorCode.NOT_FOUND);
        }
        String content = form.getContent().trim();
        if (reviewMapper.findReplyByReviewId(reviewId).isPresent()) {
            reviewMapper.updateReply(reviewId, adminId, content);
            return;
        }
        ReviewReply reply = new ReviewReply();
        reply.setReviewId(reviewId);
        reply.setAdminId(adminId);
        reply.setContent(content);
        reviewMapper.insertReply(reply);
    }
}
