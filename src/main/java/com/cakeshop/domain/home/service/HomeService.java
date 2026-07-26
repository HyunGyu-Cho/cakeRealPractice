package com.cakeshop.domain.home.service;

import com.cakeshop.domain.community.dto.view.PostSummaryView;
import com.cakeshop.domain.community.service.CommunityService;
import com.cakeshop.domain.coupon.dto.view.DownloadableCouponView;
import com.cakeshop.domain.coupon.service.CouponService;
import com.cakeshop.domain.product.dto.view.CategorySummaryView;
import com.cakeshop.domain.product.dto.view.ProductSummaryView;
import com.cakeshop.domain.product.service.ProductService;
import com.cakeshop.domain.review.dto.view.BestReviewView;
import com.cakeshop.domain.review.service.ReviewService;
import com.cakeshop.domain.store.dto.view.StorePublicView;
import com.cakeshop.domain.store.service.StoreService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class HomeService {

    private static final int MAIN_PRODUCT_COUNT = 4;
    private static final int MAIN_REVIEW_COUNT = 3;
    private static final int MAIN_POST_COUNT = 5;
    private static final int MAIN_COUPON_COUNT = 3;

    private final StoreService storeService;
    private final ProductService productService;
    private final ReviewService reviewService;
    private final CommunityService communityService;
    private final CouponService couponService;

    public HomeService(StoreService storeService, ProductService productService,
                       ReviewService reviewService, CommunityService communityService,
                       CouponService couponService) {
        this.storeService = storeService;
        this.productService = productService;
        this.reviewService = reviewService;
        this.communityService = communityService;
        this.couponService = couponService;
    }

    // Home은 전용 Mapper를 만들지 않고 각 도메인의 공개 조회 결과만 단방향으로 조합한다.
    public StorePublicView getStore() {
        return storeService.getPublicStore();
    }

    public List<CategorySummaryView> getCategories() {
        return productService.getCategorySummaries();
    }

    public List<ProductSummaryView> getPopularProducts() {
        return productService.getPopularActiveProducts(MAIN_PRODUCT_COUNT);
    }

    public List<BestReviewView> getBestReviews() {
        return reviewService.getLatestVisibleReviews(MAIN_REVIEW_COUNT);
    }

    public List<PostSummaryView> getPopularPosts() {
        return communityService.getPopularPosts(MAIN_POST_COUNT);
    }

    /**
     * 지금 받을 수 있는 쿠폰만 추린다. 비로그인(memberId == null)은 조회 자체를 하지 않고
     * 빈 목록을 돌려 화면에서 섹션이 통째로 사라지게 한다.
     */
    public List<DownloadableCouponView> getDownloadableCoupons(Long memberId) {
        if (memberId == null) {
            return List.of();
        }
        return couponService.getDownloadableCoupons(memberId).stream()
            .filter(DownloadableCouponView::downloadable)
            .limit(MAIN_COUPON_COUNT)
            .toList();
    }
}
