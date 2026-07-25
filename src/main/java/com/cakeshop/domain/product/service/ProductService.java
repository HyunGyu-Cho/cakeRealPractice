package com.cakeshop.domain.product.service;

import com.cakeshop.domain.product.dto.form.ProductSearchForm;
import com.cakeshop.domain.product.dto.view.ProductDetailView;
import com.cakeshop.domain.product.dto.view.ProductSalesInfo;
import com.cakeshop.domain.product.dto.view.ProductSummaryView;
import com.cakeshop.domain.product.entity.Product;
import com.cakeshop.domain.product.entity.ProductStatus;
import com.cakeshop.domain.product.entity.ProductType;
import com.cakeshop.domain.product.error.ProductErrorCode;
import com.cakeshop.domain.product.mapper.ProductMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;
import java.util.List;
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

    /** [공개 계약] 홈 메인 노출용 — 판매 가능 상품 최신순. */
    @Transactional(readOnly = true)
    public List<ProductSummaryView> getLatestActiveProducts(int limit) {
        return productMapper.findLatestActiveProducts(limit)
            .stream().map(ProductSummaryView::from).toList();
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
