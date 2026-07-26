package com.cakeshop.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.domain.order.dto.view.ReviewableItemView;
import com.cakeshop.domain.order.service.OrderService;
import com.cakeshop.domain.product.service.ProductService;
import com.cakeshop.domain.review.dto.form.ReviewForm;
import com.cakeshop.domain.review.dto.view.RatingStatsRow;
import com.cakeshop.domain.review.entity.Review;
import com.cakeshop.domain.review.entity.ReviewImage;
import com.cakeshop.domain.review.entity.ReviewStatus;
import com.cakeshop.domain.review.error.ReviewErrorCode;
import com.cakeshop.domain.review.mapper.ReviewMapper;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.infra.FileStorageClient;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTests {

    private static final Long MEMBER_ID = 7L;
    private static final Long ORDER_ITEM_ID = 30L;
    private static final Long PRODUCT_ID = 8L;

    @Mock private ReviewMapper reviewMapper;
    @Mock private OrderService orderService;
    @Mock private ProductService productService;
    @Mock private MemberService memberService;
    @Mock private FileStorageClient fileStorageClient;

    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(
            reviewMapper, orderService, productService, memberService, fileStorageClient);
    }

    // ==================== 작성 자격 ====================

    @Test
    void createRejectsItemThatIsNotPickedUp() {
        // order가 자격 없는 항목은 아예 돌려주지 않는다(비 PICKED_UP·타인 주문 모두 여기서 걸린다)
        when(orderService.findReviewableItem(MEMBER_ID, ORDER_ITEM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.create(MEMBER_ID, ORDER_ITEM_ID, form(5)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ReviewErrorCode.NOT_PICKED_UP);
        verify(reviewMapper, never()).insertReview(any());
    }

    @Test
    void createStoresVisibleReviewAndRefreshesProductStats() {
        givenReviewableItem();
        givenInsertAssignsId(100L);
        when(reviewMapper.findRatingStats(PRODUCT_ID))
            .thenReturn(new RatingStatsRow(3, new BigDecimal("4.33")));

        Long reviewId = reviewService.create(MEMBER_ID, ORDER_ITEM_ID, form(5));

        assertThat(reviewId).isEqualTo(100L);
        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(reviewMapper).insertReview(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ReviewStatus.VISIBLE.name());
        assertThat(captor.getValue().getProductId()).isEqualTo(PRODUCT_ID);
        // 집계는 증분이 아니라 재계산 결과를 product에 넘긴다
        verify(productService).refreshRatingStats(PRODUCT_ID, 3, new BigDecimal("4.33"));
    }

    @Test
    void concurrentSecondReviewOnSameOrderItemIsStoppedByUniqueConstraint() {
        givenReviewableItem();
        when(reviewMapper.insertReview(any(Review.class)))
            .thenThrow(new DuplicateKeyException("uk_reviews_order_item"));

        assertThatThrownBy(() -> reviewService.create(MEMBER_ID, ORDER_ITEM_ID, form(5)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ReviewErrorCode.ALREADY_WRITTEN);
    }

    @Test
    void detailRatingsAreOptional() {
        givenReviewableItem();
        givenInsertAssignsId(100L);
        when(reviewMapper.findRatingStats(PRODUCT_ID))
            .thenReturn(new RatingStatsRow(1, new BigDecimal("5.00")));

        ReviewForm form = form(5);
        form.setTasteRating(null);
        form.setDesignRating(null);
        form.setServiceRating(null);
        reviewService.create(MEMBER_ID, ORDER_ITEM_ID, form);

        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(reviewMapper).insertReview(captor.capture());
        assertThat(captor.getValue().getOverallRating()).isEqualTo(5);
        assertThat(captor.getValue().getTasteRating()).isNull();
    }

    // ==================== 작성 가능 목록 ====================

    @Test
    void reviewableItemsExcludeAlreadyWrittenOnes() {
        when(orderService.getReviewableItems(MEMBER_ID))
            .thenReturn(List.of(item(30L), item(31L)));
        when(reviewMapper.findWrittenOrderItemIds(List.of(30L, 31L))).thenReturn(List.of(30L));

        List<ReviewableItemView> items = reviewService.getReviewableItems(MEMBER_ID);

        assertThat(items).extracting(ReviewableItemView::orderItemId).containsExactly(31L);
    }

    @Test
    void noPickedUpItemMeansNoQueryToReviews() {
        when(orderService.getReviewableItems(MEMBER_ID)).thenReturn(List.of());

        assertThat(reviewService.getReviewableItems(MEMBER_ID)).isEmpty();
        verify(reviewMapper, never()).findWrittenOrderItemIds(any());
    }

    // ==================== 수정·삭제 ====================

    @Test
    void updateRejectsAnotherMembersReview() {
        when(reviewMapper.findById(100L)).thenReturn(Optional.of(review(999L)));

        assertThatThrownBy(() -> reviewService.update(MEMBER_ID, 100L, form(4)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ReviewErrorCode.NOT_OWNER);
        verify(reviewMapper, never()).updateReview(any());
    }

    @Test
    void updateKeepsExistingImagesWhenNoNewFileIsUploaded() {
        when(reviewMapper.findById(100L)).thenReturn(Optional.of(review(MEMBER_ID)));
        when(reviewMapper.findRatingStats(PRODUCT_ID))
            .thenReturn(new RatingStatsRow(1, new BigDecimal("4.00")));

        reviewService.update(MEMBER_ID, 100L, form(4));

        verify(reviewMapper).updateReview(any(Review.class));
        verify(reviewMapper, never()).deleteImagesByReviewId(anyLong());
        verify(productService).refreshRatingStats(PRODUCT_ID, 1, new BigDecimal("4.00"));
    }

    @Test
    void updateReplacesImagesWhenNewFilesAreUploaded() {
        when(reviewMapper.findById(100L)).thenReturn(Optional.of(review(MEMBER_ID)));
        when(reviewMapper.findImagesByReviewId(100L)).thenReturn(List.of(image("/uploads/old.jpg")));
        when(fileStorageClient.store(any(), any())).thenReturn("/uploads/new.jpg");
        when(reviewMapper.findRatingStats(PRODUCT_ID))
            .thenReturn(new RatingStatsRow(1, new BigDecimal("4.00")));

        ReviewForm form = form(4);
        form.setImages(List.of(jpeg()));
        reviewService.update(MEMBER_ID, 100L, form);

        verify(fileStorageClient).delete("/uploads/old.jpg");
        verify(reviewMapper).deleteImagesByReviewId(100L);
        verify(reviewMapper).insertImage(any(ReviewImage.class));
    }

    @Test
    void deleteRemovesTheRowSoTheOrderItemCanBeReviewedAgain() {
        when(reviewMapper.findById(100L)).thenReturn(Optional.of(review(MEMBER_ID)));
        when(reviewMapper.deleteReview(100L)).thenReturn(1);
        when(reviewMapper.findRatingStats(PRODUCT_ID)).thenReturn(new RatingStatsRow(0, null));

        reviewService.delete(MEMBER_ID, 100L);

        // 소프트 삭제로 남기면 uk_reviews_order_item 때문에 재작성이 영영 막힌다
        verify(reviewMapper).deleteReview(100L);
        verify(reviewMapper).deleteReplyByReviewId(100L);
        verify(reviewMapper).deleteImagesByReviewId(100L);
        // 후기가 하나도 없으면 0건·0.00으로 되돌린다(AVG는 NULL로 온다)
        verify(productService).refreshRatingStats(PRODUCT_ID, 0, BigDecimal.ZERO);
    }

    @Test
    void deleteRejectsAnotherMembersReview() {
        when(reviewMapper.findById(100L)).thenReturn(Optional.of(review(999L)));

        assertThatThrownBy(() -> reviewService.delete(MEMBER_ID, 100L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ReviewErrorCode.NOT_OWNER);
        verify(reviewMapper, never()).deleteReview(anyLong());
    }

    // ==================== 이미지 제약 ====================

    @Test
    void createRejectsMoreThanThreeImages() {
        givenReviewableItem();
        ReviewForm form = form(5);
        form.setImages(List.of(jpeg(), jpeg(), jpeg(), jpeg()));

        assertThatThrownBy(() -> reviewService.create(MEMBER_ID, ORDER_ITEM_ID, form))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ReviewErrorCode.TOO_MANY_IMAGES);
        verify(reviewMapper, never()).insertReview(any());
    }

    @Test
    void createRejectsNonImageFile() {
        givenReviewableItem();
        ReviewForm form = form(5);
        form.setImages(List.of(new MockMultipartFile(
            "images", "note.txt", "text/plain", "hello".getBytes())));

        assertThatThrownBy(() -> reviewService.create(MEMBER_ID, ORDER_ITEM_ID, form))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ReviewErrorCode.INVALID_IMAGE);
    }

    // ==================== 픽스처 ====================

    private void givenReviewableItem() {
        when(orderService.findReviewableItem(MEMBER_ID, ORDER_ITEM_ID))
            .thenReturn(Optional.of(item(ORDER_ITEM_ID)));
    }

    private void givenInsertAssignsId(long id) {
        when(reviewMapper.insertReview(any(Review.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Review.class).setId(id);
            return 1;
        });
    }

    private ReviewableItemView item(Long orderItemId) {
        return new ReviewableItemView(orderItemId, 55L, "ORD-20260726-ABC", PRODUCT_ID,
            "초코 가나슈 케이크", 1, LocalDateTime.of(2026, 7, 20, 14, 0));
    }

    private Review review(Long memberId) {
        Review review = new Review();
        review.setId(100L);
        review.setOrderItemId(ORDER_ITEM_ID);
        review.setProductId(PRODUCT_ID);
        review.setMemberId(memberId);
        review.setOverallRating(5);
        review.setStatus(ReviewStatus.VISIBLE.name());
        return review;
    }

    private ReviewImage image(String url) {
        ReviewImage image = new ReviewImage();
        image.setReviewId(100L);
        image.setImageUrl(url);
        image.setSortOrder(0);
        return image;
    }

    private ReviewForm form(int overall) {
        ReviewForm form = new ReviewForm();
        form.setOrderItemId(ORDER_ITEM_ID);
        form.setOverallRating(overall);
        form.setTasteRating(5);
        form.setContent("맛있게 잘 먹었습니다. 다음에 또 주문할게요.");
        return form;
    }

    private MockMultipartFile jpeg() {
        return new MockMultipartFile("images", "cake.jpg", "image/jpeg", new byte[] {1, 2, 3});
    }
}
