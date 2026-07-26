package com.cakeshop.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.product.dto.form.ProductSearchForm;
import com.cakeshop.domain.product.dto.view.CategorySummaryView;
import com.cakeshop.domain.product.dto.view.ProductSalesInfo;
import com.cakeshop.domain.product.dto.view.ProductSummaryView;
import com.cakeshop.domain.product.dto.view.ProductTypeCountRow;
import com.cakeshop.domain.product.entity.Product;
import com.cakeshop.domain.product.entity.ProductType;
import com.cakeshop.domain.product.error.ProductErrorCode;
import com.cakeshop.domain.product.mapper.ProductMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.error.BusinessException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceTests {

    @Mock
    private ProductMapper productMapper;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productMapper);
    }

    @Test
    void emptyResultSkipsPageQuery() {
        when(productMapper.countProducts(any(ProductSearchForm.class))).thenReturn(0L);

        var result = productService.getProductPage(new ProductSearchForm(), new PageRequest(1, 9));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        verify(productMapper, never()).findProductPage(any(), anyInt(), anyInt());
    }

    @Test
    void salesInfoIsOnSaleWhenActiveAndInStock() {
        when(productMapper.findProductById(1L)).thenReturn(Optional.of(product("ACTIVE", 5)));

        ProductSalesInfo info = productService.getSalesInfo(1L);

        assertThat(info.onSale()).isTrue();
        assertThat(info.price()).isEqualTo(35000L);
    }

    @Test
    void salesInfoIsOnSaleWhenStockNotManaged() {
        // 주문제작(재고 NULL)은 재고와 무관하게 판매 가능
        when(productMapper.findProductById(1L)).thenReturn(Optional.of(product("ACTIVE", null)));

        assertThat(productService.getSalesInfo(1L).onSale()).isTrue();
    }

    @Test
    void salesInfoIsNotOnSaleWhenSoldOutOrInactive() {
        when(productMapper.findProductById(1L)).thenReturn(Optional.of(product("ACTIVE", 0)));
        assertThat(productService.getSalesInfo(1L).onSale()).isFalse();

        when(productMapper.findProductById(2L)).thenReturn(Optional.of(product("INACTIVE", 5)));
        assertThat(productService.getSalesInfo(2L).onSale()).isFalse();
    }

    @Test
    void missingProductFails() {
        when(productMapper.findProductById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductDetail(99L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ProductErrorCode.NOT_FOUND);
    }

    @Test
    void stockLabelDerivesFromQuantity() {
        // 품절·재고부족은 stock_quantity 파생값 — 라벨 규칙 회귀 가드
        assertThat(ProductSummaryView.stockLabel(ProductType.GENERAL, 0)).isEqualTo("품절");
        assertThat(ProductSummaryView.stockLabel(ProductType.GENERAL, 4)).isEqualTo("재고 부족");
        assertThat(ProductSummaryView.stockLabel(ProductType.GENERAL, 5)).isEqualTo("재고 있음");
        assertThat(ProductSummaryView.stockLabel(ProductType.CUSTOM, null)).isEqualTo("주문 가능");
    }

    @Test
    void categorySummariesKeepAllTypesEvenWithoutProducts() {
        // 홈 카테고리 카드는 상품이 0개인 유형도 남아야 한다(enum 기준으로 만든다)
        when(productMapper.countActiveByProductType())
            .thenReturn(List.of(new ProductTypeCountRow("GENERAL", 3L)));

        List<CategorySummaryView> categories = productService.getCategorySummaries();

        assertThat(categories).hasSize(ProductType.values().length);
        assertThat(categories).extracting(CategorySummaryView::productType)
            .containsExactly("GENERAL", "CUSTOM", "SAME_DAY", "SEASON");
        assertThat(categories.get(0).productCount()).isEqualTo(3L);
        assertThat(categories.get(0).description()).isEqualTo("판매 중 3개");
        assertThat(categories.get(1).description()).isEqualTo("판매 중 0개");
    }

    private Product product(String status, Integer stock) {
        Product product = new Product();
        product.setId(1L);
        product.setName("딸기 생크림 케이크");
        product.setBasePrice(35000L);
        product.setProductType(stock == null ? "CUSTOM" : "GENERAL");
        product.setPreparationDays(0);
        product.setCancellationLimitDays(0);
        product.setStockQuantity(stock);
        product.setStatus(status);
        product.setReviewCount(0);
        return product;
    }
}
