package com.cakeshop.domain.product.service;

import com.cakeshop.domain.product.dto.form.AdminProductSearchForm;
import com.cakeshop.domain.product.dto.form.ProductForm;
import com.cakeshop.domain.product.dto.view.ProductSummaryView;
import com.cakeshop.domain.product.entity.Category;
import com.cakeshop.domain.product.entity.Product;
import com.cakeshop.domain.product.entity.ProductImage;
import com.cakeshop.domain.product.entity.ProductStatus;
import com.cakeshop.domain.product.error.ProductErrorCode;
import com.cakeshop.domain.product.mapper.ProductMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.infra.FileStorageClient;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProductAdminService {

    private static final Logger log = LoggerFactory.getLogger(ProductAdminService.class);
    private static final String IMAGE_DIRECTORY = "product";

    private final ProductMapper productMapper;
    private final FileStorageClient fileStorageClient;

    public ProductAdminService(ProductMapper productMapper, FileStorageClient fileStorageClient) {
        this.productMapper = productMapper;
        this.fileStorageClient = fileStorageClient;
    }

    @Transactional(readOnly = true)
    public PageResult<ProductSummaryView> getAdminProductPage(AdminProductSearchForm cond, PageRequest pageRequest) {
        long total = productMapper.countAdminProducts(cond);
        if (total == 0) {
            return new PageResult<>(List.of(), pageRequest, 0);
        }
        List<ProductSummaryView> content = productMapper
            .findAdminProductPage(cond, pageRequest.getSize(), pageRequest.getOffset())
            .stream().map(ProductSummaryView::from).toList();
        return new PageResult<>(content, pageRequest, total);
    }

    @Transactional(readOnly = true)
    public ProductForm getProductForm(Long productId) {
        return ProductForm.from(findProduct(productId));
    }

    @Transactional(readOnly = true)
    public String getMainImageUrl(Long productId) {
        return productMapper.findMainImage(productId)
            .map(ProductImage::getImageUrl)
            .orElse(null);
    }

    /** 등록. 대표 이미지는 선택 — 있으면 파일 저장 후 sort_order=0 행으로 남긴다. */
    @Transactional
    public Long createProduct(ProductForm form, MultipartFile image) {
        Product product = toProduct(new Product(), form);
        productMapper.insertProduct(product);
        if (hasImage(image)) {
            validateImage(image);
            ProductImage mainImage = new ProductImage();
            mainImage.setProductId(product.getId());
            mainImage.setImageUrl(fileStorageClient.store(image, IMAGE_DIRECTORY));
            mainImage.setSortOrder(0);
            productMapper.insertProductImage(mainImage);
        }
        return product.getId();
    }

    /** 수정. 새 이미지가 있을 때만 교체하고, DB 저장 성공 후 이전 파일을 삭제한다(store 패턴). */
    @Transactional
    public void updateProduct(Long productId, ProductForm form, MultipartFile image) {
        Product product = toProduct(findProduct(productId), form);
        productMapper.updateProduct(product);

        if (!hasImage(image)) {
            return;
        }
        validateImage(image);
        ProductImage existing = productMapper.findMainImage(productId).orElse(null);
        String previousUrl = existing == null ? null : existing.getImageUrl();
        String newUrl = fileStorageClient.store(image, IMAGE_DIRECTORY);
        boolean transactionSynchronized = TransactionSynchronizationManager.isSynchronizationActive();
        if (transactionSynchronized) {
            registerImageCleanup(previousUrl, newUrl);
        }

        try {
            if (existing == null) {
                ProductImage mainImage = new ProductImage();
                mainImage.setProductId(productId);
                mainImage.setImageUrl(newUrl);
                mainImage.setSortOrder(0);
                productMapper.insertProductImage(mainImage);
            } else {
                productMapper.updateProductImageUrl(existing.getId(), newUrl);
            }
        } catch (RuntimeException exception) {
            if (!transactionSynchronized) {
                deleteImageQuietly(newUrl);
            }
            throw exception;
        }

        // 프록시를 거치지 않는 단위 테스트 같은 비트랜잭션 호출도 파일 정합성을 지킨다.
        if (!transactionSynchronized) {
            deleteImageQuietly(previousUrl);
        }
    }

    /** 판매 중지/재개 토글. 하드 삭제는 하지 않는다(주문 스냅샷·FK 보존). */
    @Transactional
    public ProductStatus toggleStatus(Long productId) {
        Product product = findProduct(productId);
        ProductStatus next = ProductStatus.ACTIVE.matches(product.getStatus())
            ? ProductStatus.INACTIVE : ProductStatus.ACTIVE;
        productMapper.updateStatus(productId, next.name());
        return next;
    }

    private Product toProduct(Product product, ProductForm form) {
        Category category = productMapper.findCategoryByCode(form.getProductType().name())
            .orElseThrow(() -> new BusinessException(ProductErrorCode.CATEGORY_NOT_FOUND));
        product.setCategoryId(category.getId());
        product.setName(form.getName().trim());
        product.setDescription(trimToNull(form.getDescription()));
        product.setBasePrice(form.getBasePrice());
        product.setProductType(form.getProductType().name());
        product.setPreparationDays(form.getPreparationDays());
        // 주문제작은 재고 미관리(NULL 고정) — 폼에 값이 있어도 무시한다.
        product.setStockQuantity(form.getProductType().isStockManaged() ? form.getStockQuantity() : null);
        product.setStatus(form.getStatus().name());
        return product;
    }

    private Product findProduct(Long productId) {
        return productMapper.findProductById(productId)
            .orElseThrow(() -> new BusinessException(ProductErrorCode.NOT_FOUND));
    }

    private boolean hasImage(MultipartFile image) {
        return image != null && !image.isEmpty();
    }

    private void validateImage(MultipartFile image) {
        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException(ProductErrorCode.INVALID_IMAGE);
        }
    }

    private void registerImageCleanup(String previousUrl, String newUrl) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    deleteImageQuietly(previousUrl);
                } else {
                    deleteImageQuietly(newUrl);
                }
            }
        });
    }

    private void deleteImageQuietly(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }
        try {
            fileStorageClient.delete(imageUrl);
        } catch (RuntimeException exception) {
            log.warn("상품 이미지 파일 정리에 실패했습니다: {}", imageUrl, exception);
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
