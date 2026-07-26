package com.cakeshop.domain.product.service;

import com.cakeshop.domain.product.dto.form.ProductSearchForm;
import com.cakeshop.domain.product.dto.view.CategorySummaryView;
import com.cakeshop.domain.product.dto.view.ProductDetailView;
import com.cakeshop.domain.product.dto.view.ProductOptionGroupView;
import com.cakeshop.domain.product.dto.view.ProductOptionView;
import com.cakeshop.domain.product.dto.view.ProductSalesInfo;
import com.cakeshop.domain.product.dto.view.ProductSummaryView;
import com.cakeshop.domain.product.dto.view.ProductTypeCountRow;
import com.cakeshop.domain.product.entity.Product;
import com.cakeshop.domain.product.entity.ProductOption;
import com.cakeshop.domain.product.entity.ProductOptionGroup;
import com.cakeshop.domain.product.entity.ProductStatus;
import com.cakeshop.domain.product.entity.ProductType;
import com.cakeshop.domain.product.error.ProductErrorCode;
import com.cakeshop.domain.product.mapper.ProductMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 고객 조회 + 공개 계약. cart·order·home이 의존한다. */
@Service
public class ProductService {

    private final ProductMapper productMapper;

    public ProductService(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    @Transactional(readOnly = true)
    public PageResult<ProductSummaryView> getProductPage(ProductSearchForm cond, PageRequest pageRequest) {
        long total = productMapper.countProducts(cond);
        if (total == 0) {
            return new PageResult<>(List.of(), pageRequest, 0);
        }
        List<ProductSummaryView> content = productMapper
            .findProductPage(cond, pageRequest.getSize(), pageRequest.getOffset())
            .stream().map(ProductSummaryView::from).toList();
        return new PageResult<>(content, pageRequest, total);
    }

    @Transactional(readOnly = true)
    public ProductDetailView getProductDetail(Long productId) {
        Product product = findProduct(productId);
        ProductType type = ProductType.valueOf(product.getProductType());
        ProductStatus status = ProductStatus.valueOf(product.getStatus());
        String imageUrl = productMapper.findMainImage(productId)
            .map(image -> image.getImageUrl())
            .orElse(null);
        return new ProductDetailView(
            product.getId(), product.getName(), product.getDescription(), product.getBasePrice(),
            type.name(), type.label(), status.name(), status.label(),
            product.getStockQuantity(),
            ProductSummaryView.stockLabel(type, product.getStockQuantity()),
            product.getPreparationDays(), product.getCancellationLimitDays(),
            imageUrl, product.getAverageRating(), product.getReviewCount(),
            isOnSale(product)
        );
    }

    /**
     * [공개 계약] 1차 합의 "getSalesInfo(id) → (판매가능여부, 가격, 재고)"의 구현.
     * cart·order가 사용 예정이다 — 시그니처 변경 시 사용처(시은↔수민·주환) 합의 필요.
     */
    @Transactional(readOnly = true)
    public ProductSalesInfo getSalesInfo(Long productId) {
        Product product = findProduct(productId);
        return new ProductSalesInfo(
            product.getId(), isOnSale(product), product.getBasePrice(), product.getStockQuantity());
    }

    /**
     * order 결제 트랜잭션용 공개 계약. 조건부 UPDATE 한 번으로 동시 주문의 초과 차감을 막는다.
     */
    @Transactional
    public void decreaseStock(Long productId, int quantity) {
        if (quantity < 1 || productMapper.decreaseStockIfAvailable(productId, quantity) != 1) {
            throw new BusinessException(ProductErrorCode.INSUFFICIENT_STOCK);
        }
    }

    /** 취소 트랜잭션용 공개 계약. 일반 상품의 차감 수량을 정확히 복구한다. */
    @Transactional
    public void restoreStock(Long productId, int quantity) {
        if (quantity < 1 || productMapper.increaseStock(productId, quantity) != 1) {
            throw new BusinessException(ProductErrorCode.STOCK_UPDATE_FAILED);
        }
    }

    /**
     * [공개 계약] 후기 집계를 반영한다. review 도메인이 자기 테이블에서 계산한 값을 받아 쓰기만 한다 —
     * 시그니처 변경 시 사용처(시은↔현규) 합의 필요.
     *
     * <p>상품이 지워졌으면 영향 행이 0이지만 예외로 올리지 않는다. 후기 저장·삭제 트랜잭션이
     * 집계 때문에 통째로 되돌아갈 이유는 없다.
     */
    @Transactional
    public void refreshRatingStats(Long productId, long reviewCount, BigDecimal averageRating) {
        productMapper.updateRatingStats(productId, reviewCount, averageRating);
    }

    /**
     * [공개 계약] 상품의 옵션 그룹과 판매 중인 옵션을 함께 반환한다.
     * order(수제)가 요청서 화면을 그리고 선택값·추가 금액을 검증할 때 사용한다 —
     * 시그니처 변경 시 사용처(시은↔주환) 합의 필요.
     * 옵션이 없는 상품은 빈 목록이며, 값이 모두 INACTIVE인 그룹은 빈 options로 남는다.
     */
    @Transactional(readOnly = true)
    public List<ProductOptionGroupView> getOptionGroups(Long productId) {
        List<ProductOptionGroup> groups = productMapper.findOptionGroups(productId);
        if (groups.isEmpty()) {
            return List.of();
        }
        Map<Long, List<ProductOptionView>> optionsByGroup = productMapper
            .findActiveOptionsByGroupIds(groups.stream().map(ProductOptionGroup::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(ProductOption::getOptionGroupId, LinkedHashMap::new,
                Collectors.mapping(
                    option -> new ProductOptionView(
                        option.getId(), option.getName(), option.getAdditionalPrice()),
                    Collectors.toList())));
        return groups.stream()
            .map(group -> new ProductOptionGroupView(
                group.getId(), group.getName(),
                Boolean.TRUE.equals(group.getRequired()), group.getSelectionType(),
                optionsByGroup.getOrDefault(group.getId(), List.of())))
            .toList();
    }

    /** [공개 계약] 판매 가능 상품 최신순. order(수제)가 기본 상품을 고를 때 쓴다. */
    @Transactional(readOnly = true)
    public List<ProductSummaryView> getLatestActiveProducts(int limit) {
        return productMapper.findLatestActiveProducts(limit)
            .stream().map(ProductSummaryView::from).toList();
    }

    /** [공개 계약] 홈 메인 노출용 — 판매 가능 상품을 후기 수·평점 순으로. */
    @Transactional(readOnly = true)
    public List<ProductSummaryView> getPopularActiveProducts(int limit) {
        return productMapper.findPopularActiveProducts(limit)
            .stream().map(ProductSummaryView::from).toList();
    }

    /**
     * [공개 계약] 홈 카테고리 카드용 — 유형 4개를 enum 순서대로 채운다.
     * 판매 가능 상품이 없는 유형도 "판매 중 0개"로 남겨 카드가 사라지지 않게 한다.
     */
    @Transactional(readOnly = true)
    public List<CategorySummaryView> getCategorySummaries() {
        Map<String, Long> counts = productMapper.countActiveByProductType().stream()
            .collect(Collectors.toMap(
                ProductTypeCountRow::productType, ProductTypeCountRow::productCount));
        return Arrays.stream(ProductType.values())
            .map(type -> {
                long count = counts.getOrDefault(type.name(), 0L);
                return new CategorySummaryView(
                    type.name(), type.label(), "판매 중 " + count + "개", count);
            })
            .toList();
    }

    private Product findProduct(Long productId) {
        return productMapper.findProductById(productId)
            .orElseThrow(() -> new BusinessException(ProductErrorCode.NOT_FOUND));
    }

    // 판매 가능 = 판매 스위치 ACTIVE + (재고 미관리 상품이거나 재고 > 0)
    private boolean isOnSale(Product product) {
        boolean active = ProductStatus.ACTIVE.matches(product.getStatus());
        boolean inStock = product.getStockQuantity() == null || product.getStockQuantity() > 0;
        return active && inStock;
    }
}
