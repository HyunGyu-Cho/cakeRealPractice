package com.cakeshop.domain.product.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.product.dto.form.AdminProductSearchForm;
import com.cakeshop.domain.product.dto.form.ProductForm;
import com.cakeshop.domain.product.entity.ProductStatus;
import com.cakeshop.domain.product.service.ProductAdminService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ProductAdminControllerTests {

    @Mock
    private ProductAdminService productAdminService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProductAdminController(productAdminService)).build();
    }

    @Test
    void listProvidesPageResult() throws Exception {
        when(productAdminService.getAdminProductPage(any(AdminProductSearchForm.class), any(PageRequest.class)))
            .thenReturn(new PageResult<>(List.of(), new PageRequest(1, 10), 0));

        mockMvc.perform(get("/admin/products"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/product/list"))
            .andExpect(model().attributeExists("pageResult", "extraQuery"));
    }

    @Test
    void createFormProvidesEmptyFormAndReferenceData() throws Exception {
        mockMvc.perform(get("/admin/products/new"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/product/form"))
            .andExpect(model().attributeExists("productForm", "productTypes", "productStatuses"));
    }

    @Test
    void invalidCreateRendersSameFormWithoutCallingService() throws Exception {
        mockMvc.perform(post("/admin/products").param("name", ""))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/product/form"))
            .andExpect(model().attributeHasFieldErrors("productForm", "name", "productType", "basePrice"));

        verify(productAdminService, never()).createProduct(any(ProductForm.class), any());
    }

    @Test
    void stockRequiredForStockManagedTypes() throws Exception {
        // 재고 관리 유형(GENERAL)인데 재고를 비우면 교차 검증(@AssertTrue)에 걸린다
        mockMvc.perform(post("/admin/products")
                .param("name", "딸기 생크림 케이크")
                .param("productType", "GENERAL")
                .param("basePrice", "35000")
                .param("preparationDays", "0")
                .param("status", "ACTIVE"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("productForm", "stockProvided"));

        verify(productAdminService, never()).createProduct(any(ProductForm.class), any());
    }

    @Test
    void validCreateRedirectsWithFlashMessage() throws Exception {
        mockMvc.perform(validCreateRequest())
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/products"))
            .andExpect(flash().attribute("successMessage", "상품을 등록했습니다."));

        verify(productAdminService).createProduct(any(ProductForm.class), any());
    }

    @Test
    void editFormLoadsExistingProduct() throws Exception {
        ProductForm form = new ProductForm();
        form.setName("딸기 생크림 케이크");
        when(productAdminService.getProductForm(1L)).thenReturn(form);
        when(productAdminService.getMainImageUrl(1L)).thenReturn("/uploads/product/202607/a.jpg");

        mockMvc.perform(get("/admin/products/1/edit"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/product/form"))
            .andExpect(model().attribute("editingProductId", 1L))
            .andExpect(model().attribute("currentImageUrl", "/uploads/product/202607/a.jpg"));
    }

    @Test
    void toggleStatusRedirectsWithFlashMessage() throws Exception {
        when(productAdminService.toggleStatus(1L)).thenReturn(ProductStatus.INACTIVE);

        mockMvc.perform(post("/admin/products/1/status"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/products"))
            .andExpect(flash().attribute("successMessage", "판매를 중지했습니다."));

        verify(productAdminService).toggleStatus(eq(1L));
    }

    private MockHttpServletRequestBuilder validCreateRequest() {
        return post("/admin/products")
            .param("name", "딸기 생크림 케이크")
            .param("productType", "GENERAL")
            .param("basePrice", "35000")
            .param("stockQuantity", "12")
            .param("preparationDays", "0")
            .param("status", "ACTIVE");
    }
}
