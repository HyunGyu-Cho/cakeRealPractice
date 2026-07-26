package com.cakeshop.domain.product.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.product.dto.form.ProductSearchForm;
import com.cakeshop.domain.product.dto.view.ProductDetailView;
import com.cakeshop.domain.product.service.ProductService;
import com.cakeshop.domain.review.service.ReviewService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ProductControllerTests {

    @Mock
    private ProductService productService;
    @Mock
    private ReviewService reviewService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new ProductController(productService, reviewService)).build();
    }

    @Test
    void listProvidesPageResultAndQueryStrings() throws Exception {
        when(productService.getProductPage(any(ProductSearchForm.class), any(PageRequest.class)))
            .thenReturn(new PageResult<>(List.of(), new PageRequest(1, 9), 0));

        mockMvc.perform(get("/products").param("type", "general").param("keyword", "cake"))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/product/list"))
            .andExpect(model().attributeExists("pageResult", "filterQuery", "extraQuery", "currentSort"))
            // 소문자 type 쿼리(general)는 대문자 enum 이름으로 정규화되어 링크에 유지된다
            .andExpect(model().attribute("filterQuery", "&type=GENERAL&keyword=cake"))
            .andExpect(model().attribute("currentSort", "latest"));
    }

    @Test
    void detailProvidesProductViewAndPublicReviews() throws Exception {
        when(productService.getProductDetail(5L)).thenReturn(detailView());
        // 후기는 컨트롤러에서 조합한다 — ProductService가 ReviewService를 부르면 서비스 간 순환이 된다.
        when(reviewService.getProductReviews(eq(5L), any(PageRequest.class)))
            .thenReturn(new PageResult<>(List.of(), new PageRequest(1, 5), 0));

        mockMvc.perform(get("/products/5"))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/product/detail"))
            .andExpect(model().attributeExists("product", "reviewPage"));
    }

    @Test
    void detailRequiresNumericId() throws Exception {
        // 숫자가 아닌 경로는 매핑되지 않는다({productId:\d+})
        mockMvc.perform(get("/products/abc")).andExpect(status().isNotFound());
    }

    private ProductDetailView detailView() {
        return new ProductDetailView(5L, "딸기 생크림 케이크", "설명", 35000L,
            "GENERAL", "일반 케이크", "ACTIVE", "판매 중", 12, "재고 있음",
            0, 0, null, new BigDecimal("0.00"), 0, true);
    }
}
