package com.cakeshop.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.product.dto.form.ProductForm;
import com.cakeshop.domain.product.entity.Category;
import com.cakeshop.domain.product.entity.Product;
import com.cakeshop.domain.product.entity.ProductImage;
import com.cakeshop.domain.product.entity.ProductStatus;
import com.cakeshop.domain.product.entity.ProductType;
import com.cakeshop.domain.product.error.ProductErrorCode;
import com.cakeshop.domain.product.mapper.ProductMapper;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.infra.FileStorageClient;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class ProductAdminServiceTests {

    @Mock
    private ProductMapper productMapper;

    @Mock
    private FileStorageClient fileStorageClient;

    private ProductAdminService productAdminService;

    @BeforeEach
    void setUp() {
        productAdminService = new ProductAdminService(productMapper, fileStorageClient);
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void createStoresImageAndInsertsMainImageRow() {
        when(productMapper.findCategoryByCode("NORMAL")).thenReturn(Optional.of(category(1L, "NORMAL")));
        when(fileStorageClient.store(any(), any())).thenReturn("/uploads/product/202607/a.jpg");
        MockMultipartFile image = new MockMultipartFile("image", "cake.jpg", "image/jpeg", new byte[] {1});

        productAdminService.createProduct(form(ProductType.NORMAL, 12), image);

        ArgumentCaptor<ProductImage> captor = ArgumentCaptor.forClass(ProductImage.class);
        verify(productMapper).insertProduct(any(Product.class));
        verify(productMapper).insertProductImage(captor.capture());
        assertThat(captor.getValue().getImageUrl()).isEqualTo("/uploads/product/202607/a.jpg");
        assertThat(captor.getValue().getSortOrder()).isZero();
    }

    @Test
    void customProductIgnoresStockInput() {
        when(productMapper.findCategoryByCode("CUSTOM")).thenReturn(Optional.of(category(2L, "CUSTOM")));

        productAdminService.createProduct(form(ProductType.CUSTOM, 12), null);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productMapper).insertProduct(captor.capture());
        // 주문제작은 재고 미관리 — 폼에 값이 있어도 NULL 저장
        assertThat(captor.getValue().getStockQuantity()).isNull();
        assertThat(captor.getValue().getCategoryId()).isEqualTo(2L);
    }

    @Test
    void nonImageUploadIsRejected() {
        when(productMapper.findCategoryByCode("NORMAL")).thenReturn(Optional.of(category(1L, "NORMAL")));
        MockMultipartFile file = new MockMultipartFile("image", "malware.exe", "application/octet-stream", new byte[] {1});

        assertThatThrownBy(() -> productAdminService.createProduct(form(ProductType.NORMAL, 12), file))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ProductErrorCode.INVALID_IMAGE);
    }

    @Test
    void updateReplacesImageAndDeletesPreviousFileAfterDbWrite() {
        when(productMapper.findProductById(1L)).thenReturn(Optional.of(existingProduct()));
        when(productMapper.findCategoryByCode("NORMAL")).thenReturn(Optional.of(category(1L, "NORMAL")));
        ProductImage existing = new ProductImage();
        existing.setId(10L);
        existing.setProductId(1L);
        existing.setImageUrl("/uploads/product/202606/old.jpg");
        existing.setSortOrder(0);
        when(productMapper.findMainImage(1L)).thenReturn(Optional.of(existing));
        when(fileStorageClient.store(any(), any())).thenReturn("/uploads/product/202607/new.jpg");
        MockMultipartFile image = new MockMultipartFile("image", "new.jpg", "image/png", new byte[] {1});

        productAdminService.updateProduct(1L, form(ProductType.NORMAL, 5), image);

        verify(productMapper).updateProductImageUrl(10L, "/uploads/product/202607/new.jpg");
        verify(fileStorageClient).delete("/uploads/product/202606/old.jpg");
    }

    @Test
    void updateDefersPreviousImageDeletionUntilTransactionCommit() {
        when(productMapper.findProductById(1L)).thenReturn(Optional.of(existingProduct()));
        when(productMapper.findCategoryByCode("NORMAL")).thenReturn(Optional.of(category(1L, "NORMAL")));
        ProductImage existing = new ProductImage();
        existing.setId(10L);
        existing.setProductId(1L);
        existing.setImageUrl("/uploads/product/202606/old.jpg");
        existing.setSortOrder(0);
        when(productMapper.findMainImage(1L)).thenReturn(Optional.of(existing));
        when(fileStorageClient.store(any(), any())).thenReturn("/uploads/product/202607/new.jpg");
        MockMultipartFile image = new MockMultipartFile("image", "new.jpg", "image/png", new byte[] {1});
        TransactionSynchronizationManager.initSynchronization();

        productAdminService.updateProduct(1L, form(ProductType.NORMAL, 5), image);

        verify(fileStorageClient, never()).delete(any());
        TransactionSynchronizationManager.getSynchronizations().forEach(
            synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        verify(fileStorageClient).delete("/uploads/product/202606/old.jpg");
        verify(fileStorageClient, never()).delete("/uploads/product/202607/new.jpg");
    }

    @Test
    void updateDeletesNewImageAndKeepsPreviousImageAfterTransactionRollback() {
        when(productMapper.findProductById(1L)).thenReturn(Optional.of(existingProduct()));
        when(productMapper.findCategoryByCode("NORMAL")).thenReturn(Optional.of(category(1L, "NORMAL")));
        ProductImage existing = new ProductImage();
        existing.setId(10L);
        existing.setProductId(1L);
        existing.setImageUrl("/uploads/product/202606/old.jpg");
        existing.setSortOrder(0);
        when(productMapper.findMainImage(1L)).thenReturn(Optional.of(existing));
        when(fileStorageClient.store(any(), any())).thenReturn("/uploads/product/202607/new.jpg");
        MockMultipartFile image = new MockMultipartFile("image", "new.jpg", "image/png", new byte[] {1});
        TransactionSynchronizationManager.initSynchronization();

        productAdminService.updateProduct(1L, form(ProductType.NORMAL, 5), image);

        TransactionSynchronizationManager.getSynchronizations().forEach(
            synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(fileStorageClient).delete("/uploads/product/202607/new.jpg");
        verify(fileStorageClient, never()).delete("/uploads/product/202606/old.jpg");
    }

    @Test
    void updateWithoutImageKeepsExistingFile() {
        when(productMapper.findProductById(1L)).thenReturn(Optional.of(existingProduct()));
        when(productMapper.findCategoryByCode("NORMAL")).thenReturn(Optional.of(category(1L, "NORMAL")));

        productAdminService.updateProduct(1L, form(ProductType.NORMAL, 5), null);

        verify(productMapper).updateProduct(any(Product.class));
        verify(fileStorageClient, never()).delete(any());
    }

    @Test
    void toggleStatusFlipsActiveToInactive() {
        when(productMapper.findProductById(1L)).thenReturn(Optional.of(existingProduct()));

        ProductStatus next = productAdminService.toggleStatus(1L);

        assertThat(next).isEqualTo(ProductStatus.INACTIVE);
        verify(productMapper).updateStatus(1L, "INACTIVE");
    }

    @Test
    void missingCategorySeedFailsExplicitly() {
        when(productMapper.findCategoryByCode("NORMAL")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productAdminService.createProduct(form(ProductType.NORMAL, 12), null))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ProductErrorCode.CATEGORY_NOT_FOUND);
    }

    private ProductForm form(ProductType type, Integer stock) {
        ProductForm form = new ProductForm();
        form.setName("딸기 생크림 케이크");
        form.setProductType(type);
        form.setBasePrice(35000L);
        form.setStockQuantity(stock);
        form.setPreparationDays(0);
        form.setStatus(ProductStatus.ACTIVE);
        return form;
    }

    private Product existingProduct() {
        Product product = new Product();
        product.setId(1L);
        product.setCategoryId(1L);
        product.setName("딸기 생크림 케이크");
        product.setBasePrice(35000L);
        product.setProductType("NORMAL");
        product.setPreparationDays(0);
        product.setStockQuantity(12);
        product.setStatus("ACTIVE");
        return product;
    }

    private Category category(Long id, String code) {
        Category category = new Category();
        category.setId(id);
        category.setCode(code);
        category.setName(code);
        return category;
    }
}
