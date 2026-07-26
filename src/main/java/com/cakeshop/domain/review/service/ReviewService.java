package com.cakeshop.domain.review.service;

import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.domain.order.dto.view.ReviewableItemView;
import com.cakeshop.domain.order.service.OrderService;
import com.cakeshop.domain.product.service.ProductService;
import com.cakeshop.domain.review.dto.form.ReviewForm;
import com.cakeshop.domain.review.dto.view.AdminReviewRow;
import com.cakeshop.domain.review.dto.view.BestReviewView;
import com.cakeshop.domain.review.dto.view.MyReviewView;
import com.cakeshop.domain.review.dto.view.ProductReviewView;
import com.cakeshop.domain.review.dto.view.RatingStatsRow;
import com.cakeshop.domain.review.dto.view.ReviewRow;
import com.cakeshop.domain.review.dto.view.ReviewStatsView;
import com.cakeshop.domain.review.dto.view.ReviewSummaryView;
import com.cakeshop.domain.review.entity.Review;
import com.cakeshop.domain.review.entity.ReviewImage;
import com.cakeshop.domain.review.entity.ReviewStatus;
import com.cakeshop.domain.review.error.ReviewErrorCode;
import com.cakeshop.domain.review.mapper.ReviewMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.infra.FileStorageClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 고객 후기 흐름 — 작성 가능 목록, 작성·수정·삭제, 내 후기함, 상품 상세 공개 후기.
 *
 * <p>작성 자격은 order의 공개 계약으로만 확인한다({@code orders}·{@code order_items} 직접 조회 금지).
 * 상품 평점 집계는 {@code ProductService.refreshRatingStats}에 쓰기만 위임하며,
 * 계산은 이 클래스가 자기 테이블로 한다.
 *
 * <p>집계는 <b>증분이 아니라 재계산</b>이다 — 작성·수정·삭제·숨김·복구가 섞이면 증분 가감은 조용히 어긋난다.
 */
@Service
public class ReviewService {

    static final String IMAGE_DIRECTORY = "review";
    static final int MAX_IMAGES = 3;
    static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png");

    private final ReviewMapper reviewMapper;
    private final OrderService orderService;
    private final ProductService productService;
    private final MemberService memberService;
    private final FileStorageClient fileStorageClient;

    public ReviewService(ReviewMapper reviewMapper, OrderService orderService,
                         ProductService productService, MemberService memberService,
                         FileStorageClient fileStorageClient) {
        this.reviewMapper = reviewMapper;
        this.orderService = orderService;
        this.productService = productService;
        this.memberService = memberService;
        this.fileStorageClient = fileStorageClient;
    }

    // ==================== 조회 ====================

    /** 아직 후기를 쓰지 않은 픽업 완료 주문 항목. "작성할 후기" 목록이다. */
    @Transactional(readOnly = true)
    public List<ReviewableItemView> getReviewableItems(Long memberId) {
        List<ReviewableItemView> pickedUp = orderService.getReviewableItems(memberId);
        if (pickedUp.isEmpty()) {
            return List.of();
        }
        Set<Long> written = new HashSet<>(reviewMapper.findWrittenOrderItemIds(
            pickedUp.stream().map(ReviewableItemView::orderItemId).toList()));
        return pickedUp.stream()
            .filter(item -> !written.contains(item.orderItemId()))
            .toList();
    }

    /** 내 후기함. 숨김 후기도 본인에게는 "숨김 처리됨"으로 보여준다(스펙 6장 규칙 6). */
    @Transactional(readOnly = true)
    public List<MyReviewView> getMyReviews(Long memberId) {
        List<AdminReviewRow> rows = reviewMapper.findByMemberId(memberId);
        Map<Long, List<String>> images = imageUrlsOf(rows.stream().map(AdminReviewRow::id).toList());
        return rows.stream().map(row -> {
            ReviewStatus status = ReviewStatus.valueOf(row.status());
            return new MyReviewView(
                row.id(), row.productId(), row.productName(), row.overallRating(),
                row.tasteRating(), row.designRating(), row.serviceRating(), row.content(),
                images.getOrDefault(row.id(), List.of()),
                status.name(), status.label(), status == ReviewStatus.HIDDEN,
                row.createdAt(), row.replyContent());
        }).toList();
    }

    /** [공개 계약] 상품 상세의 공개 후기. product 도메인이 아니라 화면 조합 계층이 호출한다. */
    @Transactional(readOnly = true)
    public PageResult<ProductReviewView> getProductReviews(Long productId, PageRequest pageRequest) {
        long total = reviewMapper.countVisibleByProductId(productId);
        if (total == 0) {
            return new PageResult<>(List.of(), pageRequest, 0);
        }
        List<ReviewRow> rows = reviewMapper.findVisibleByProductId(
            productId, pageRequest.getSize(), pageRequest.getOffset());
        Map<Long, List<String>> images = imageUrlsOf(rows.stream().map(ReviewRow::id).toList());
        Map<Long, String> nicknames = memberService.getNicknameMap(
            rows.stream().map(ReviewRow::memberId).collect(Collectors.toSet()));
        List<ProductReviewView> content = rows.stream().map(row -> new ProductReviewView(
            row.id(), nicknames.getOrDefault(row.memberId(), "알 수 없음"),
            row.overallRating(), row.tasteRating(), row.designRating(), row.serviceRating(),
            row.content(), images.getOrDefault(row.id(), List.of()), row.createdAt(),
            row.replyContent(), row.replyCreatedAt())).toList();
        return new PageResult<>(content, pageRequest, total);
    }

    /**
     * [공개 계약] 홈 메인의 베스트 후기 — 공개 후기 최신순. 화면 조합 계층이 호출한다.
     * 이미지는 첫 장만 썸네일로 내보낸다.
     */
    @Transactional(readOnly = true)
    public List<BestReviewView> getLatestVisibleReviews(int limit) {
        if (limit < 1) {
            return List.of();
        }
        List<AdminReviewRow> rows = reviewMapper.findLatestVisible(limit);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, List<String>> images = imageUrlsOf(rows.stream().map(AdminReviewRow::id).toList());
        Map<Long, String> nicknames = memberService.getNicknameMap(
            rows.stream().map(AdminReviewRow::memberId).collect(Collectors.toSet()));
        return rows.stream().map(row -> {
            List<String> imageUrls = images.getOrDefault(row.id(), List.of());
            return new BestReviewView(
                row.id(), row.productId(), row.productName(),
                nicknames.getOrDefault(row.memberId(), "알 수 없음"),
                row.overallRating(), row.content(),
                imageUrls.isEmpty() ? null : imageUrls.get(0),
                row.createdAt());
        }).toList();
    }

    /** [공개 계약] 상품의 후기 집계. */
    @Transactional(readOnly = true)
    public ReviewSummaryView getProductReviewSummary(Long productId) {
        RatingStatsRow stats = reviewMapper.findRatingStats(productId);
        if (stats == null || stats.reviewCount() == 0) {
            return ReviewSummaryView.empty();
        }
        return new ReviewSummaryView(stats.reviewCount(), stats.averageRating());
    }

    /**
     * [공개 계약] 기간 후기 요약(작성 수·평균 평점). statistics 통계 화면이 첫 사용처다.
     * 숨김 후기는 두 값 모두에서 빠진다.
     */
    @Transactional(readOnly = true)
    public ReviewStatsView getReviewStats(LocalDate from, LocalDate to) {
        ReviewStatsView stats = reviewMapper.aggregateReviewStats(
            from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        return stats == null ? ReviewStatsView.empty() : stats;
    }

    /** [공개 계약] 답글이 없는 노출 후기 수. 대시보드의 "처리할 작업"이 쓴다. */
    @Transactional(readOnly = true)
    public long countUnansweredVisibleReviews() {
        return reviewMapper.countUnansweredVisible();
    }

    /** 수정 화면용. 본인 후기가 아니면 거부한다. */
    @Transactional(readOnly = true)
    public ReviewForm getReviewForEdit(Long memberId, Long reviewId) {
        return ReviewForm.from(requireOwnedReview(memberId, reviewId));
    }

    @Transactional(readOnly = true)
    public List<String> getImageUrls(Long reviewId) {
        return reviewMapper.findImagesByReviewId(reviewId).stream()
            .map(ReviewImage::getImageUrl).toList();
    }

    /** 작성·수정 화면 상단에 보여줄 주문 항목 정보. 자격이 없으면 여기서 걸린다. */
    @Transactional(readOnly = true)
    public ReviewableItemView getReviewTarget(Long memberId, Long orderItemId) {
        return orderService.findReviewableItem(memberId, orderItemId)
            .orElseThrow(() -> new BusinessException(ReviewErrorCode.NOT_PICKED_UP));
    }

    // ==================== 작성·수정·삭제 ====================

    /**
     * 후기 작성. 자격 확인 → 저장 → 이미지 → 집계 재계산을 한 트랜잭션으로 처리한다.
     * 주문 항목당 1개는 앱에서 먼저 보고, 동시 요청은 {@code uk_reviews_order_item}이 막는다.
     */
    @Transactional
    public Long create(Long memberId, Long orderItemId, ReviewForm form) {
        ReviewableItemView target = getReviewTarget(memberId, orderItemId);
        List<MultipartFile> images = validateImages(form.getImages());

        Review review = new Review();
        review.setOrderItemId(orderItemId);
        review.setProductId(target.productId());
        review.setMemberId(memberId);
        applyRatings(review, form);
        review.setStatus(ReviewStatus.VISIBLE.name());
        try {
            if (reviewMapper.insertReview(review) != 1 || review.getId() == null) {
                throw new BusinessException(ReviewErrorCode.SAVE_FAILED);
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ReviewErrorCode.ALREADY_WRITTEN);
        }

        storeImages(review.getId(), images);
        refreshStats(target.productId());
        return review.getId();
    }

    /** 수정. 새 이미지를 올린 경우에만 기존 이미지를 통째로 교체한다. */
    @Transactional
    public void update(Long memberId, Long reviewId, ReviewForm form) {
        Review review = requireOwnedReview(memberId, reviewId);
        List<MultipartFile> images = validateImages(form.getImages());

        applyRatings(review, form);
        reviewMapper.updateReview(review);

        if (!images.isEmpty()) {
            deleteImages(reviewId);
            storeImages(reviewId, images);
        }
        refreshStats(review.getProductId());
    }

    /**
     * 삭제. 상태로 남기지 않고 행을 지운다 — 그래야 같은 주문 항목에 다시 쓸 수 있다.
     * 이미지 파일·행과 답글까지 함께 정리한 뒤 집계를 되돌린다.
     */
    @Transactional
    public void delete(Long memberId, Long reviewId) {
        Review review = requireOwnedReview(memberId, reviewId);
        deleteImages(reviewId);
        reviewMapper.deleteReplyByReviewId(reviewId);
        if (reviewMapper.deleteReview(reviewId) != 1) {
            throw new BusinessException(ReviewErrorCode.NOT_FOUND);
        }
        refreshStats(review.getProductId());
    }

    // ==================== 내부 헬퍼 ====================

    /**
     * 상품 집계를 다시 계산해 product에 넘긴다. 후기가 없으면 0/0.00으로 되돌린다.
     * 관리자 숨김·복구도 같은 경로를 쓴다.
     */
    void refreshStats(Long productId) {
        RatingStatsRow stats = reviewMapper.findRatingStats(productId);
        long count = stats == null ? 0 : stats.reviewCount();
        BigDecimal average = stats == null || stats.averageRating() == null
            ? BigDecimal.ZERO : stats.averageRating();
        productService.refreshRatingStats(productId, count, average);
    }

    private Review requireOwnedReview(Long memberId, Long reviewId) {
        Review review = reviewMapper.findById(reviewId)
            .orElseThrow(() -> new BusinessException(ReviewErrorCode.NOT_FOUND));
        if (!review.getMemberId().equals(memberId)) {
            throw new BusinessException(ReviewErrorCode.NOT_OWNER);
        }
        return review;
    }

    private void applyRatings(Review review, ReviewForm form) {
        review.setOverallRating(form.getOverallRating());
        review.setTasteRating(form.getTasteRating());
        review.setDesignRating(form.getDesignRating());
        review.setServiceRating(form.getServiceRating());
        review.setContent(form.getContent() == null || form.getContent().isBlank()
            ? null : form.getContent().trim());
    }

    private void storeImages(Long reviewId, List<MultipartFile> images) {
        int sortOrder = 0;
        for (MultipartFile image : images) {
            ReviewImage row = new ReviewImage();
            row.setReviewId(reviewId);
            row.setImageUrl(fileStorageClient.store(image, IMAGE_DIRECTORY));
            row.setSortOrder(sortOrder++);
            reviewMapper.insertImage(row);
        }
    }

    private void deleteImages(Long reviewId) {
        reviewMapper.findImagesByReviewId(reviewId)
            .forEach(image -> fileStorageClient.delete(image.getImageUrl()));
        reviewMapper.deleteImagesByReviewId(reviewId);
    }

    /** 후기 id별 이미지 URL. 목록에서 후기마다 조회하면 N+1이라 한 번에 읽는다. */
    private Map<Long, List<String>> imageUrlsOf(Collection<Long> reviewIds) {
        if (reviewIds.isEmpty()) {
            return Map.of();
        }
        return reviewMapper.findImagesByReviewIds(reviewIds).stream().collect(Collectors.groupingBy(
            ReviewImage::getReviewId,
            Collectors.mapping(ReviewImage::getImageUrl, Collectors.toList())));
    }

    /** chat·주문제작과 같은 제약(최대 3장, 장당 5MB, JPG/PNG). */
    private List<MultipartFile> validateImages(List<MultipartFile> images) {
        if (images == null) {
            return List.of();
        }
        List<MultipartFile> present = images.stream()
            .filter(image -> image != null && !image.isEmpty())
            .toList();
        if (present.size() > MAX_IMAGES) {
            throw new BusinessException(ReviewErrorCode.TOO_MANY_IMAGES);
        }
        for (MultipartFile image : present) {
            if (image.getSize() > MAX_IMAGE_SIZE) {
                throw new BusinessException(ReviewErrorCode.IMAGE_TOO_LARGE);
            }
            String contentType = image.getContentType();
            if (contentType == null || !IMAGE_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
                throw new BusinessException(ReviewErrorCode.INVALID_IMAGE);
            }
        }
        return present;
    }
}
