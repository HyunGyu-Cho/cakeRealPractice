package com.cakeshop.domain.review.mapper;

import com.cakeshop.domain.review.dto.form.AdminReviewSearchForm;
import com.cakeshop.domain.review.dto.view.AdminReviewRow;
import com.cakeshop.domain.review.dto.view.RatingStatsRow;
import com.cakeshop.domain.review.dto.view.ReviewRow;
import com.cakeshop.domain.review.dto.view.ReviewStatsView;
import com.cakeshop.domain.review.entity.Review;
import com.cakeshop.domain.review.entity.ReviewImage;
import com.cakeshop.domain.review.entity.ReviewReply;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReviewMapper {

    // ==================== 후기 ====================

    int insertReview(Review review);

    int updateReview(Review review);

    int deleteReview(@Param("id") Long id);

    Optional<Review> findById(@Param("id") Long id);

    /** 상품 상세용 공개 후기. VISIBLE만 나간다. */
    long countVisibleByProductId(@Param("productId") Long productId);

    List<ReviewRow> findVisibleByProductId(@Param("productId") Long productId,
                                           @Param("size") int size,
                                           @Param("offset") int offset);

    /** 홈 베스트 후기. 상품명이 필요해 관리자 목록과 같은 행을 쓴다. */
    List<AdminReviewRow> findLatestVisible(@Param("limit") int limit);

    /** 내 후기함. 숨김 후기도 본인에게는 보여준다. */
    List<AdminReviewRow> findByMemberId(@Param("memberId") Long memberId);

    /** 이미 후기를 쓴 주문 항목 id. 작성 가능 목록에서 걸러내는 데 쓴다. */
    List<Long> findWrittenOrderItemIds(@Param("orderItemIds") Collection<Long> orderItemIds);

    /** 상품 평점 집계. VISIBLE 후기의 종합 평점만 센다. */
    RatingStatsRow findRatingStats(@Param("productId") Long productId);

    int updateStatus(@Param("id") Long id,
                     @Param("currentStatus") String currentStatus,
                     @Param("nextStatus") String nextStatus);

    // ==================== 관리자 목록 ====================

    long countAdminReviews(@Param("cond") AdminReviewSearchForm cond);

    List<AdminReviewRow> findAdminReviewPage(@Param("cond") AdminReviewSearchForm cond,
                                             @Param("size") int size,
                                             @Param("offset") int offset);

    // ==================== 이미지 ====================

    int insertImage(ReviewImage image);

    List<ReviewImage> findImagesByReviewId(@Param("reviewId") Long reviewId);

    List<ReviewImage> findImagesByReviewIds(@Param("reviewIds") Collection<Long> reviewIds);

    int deleteImagesByReviewId(@Param("reviewId") Long reviewId);

    // ==================== 답글 ====================

    int insertReply(ReviewReply reply);

    int updateReply(@Param("reviewId") Long reviewId,
                    @Param("adminId") Long adminId,
                    @Param("content") String content);

    Optional<ReviewReply> findReplyByReviewId(@Param("reviewId") Long reviewId);

    int deleteReplyByReviewId(@Param("reviewId") Long reviewId);

    // ==================== 통계 집계 (statistics가 공개 계약으로 사용한다) ====================

    ReviewStatsView aggregateReviewStats(@Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to);

    /** 답글이 없는 노출 후기 수. 미답변은 저장하지 않는 파생값이다. */
    long countUnansweredVisible();
}
