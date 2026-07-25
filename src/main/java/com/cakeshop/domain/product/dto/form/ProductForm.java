package com.cakeshop.domain.product.dto.form;

import com.cakeshop.domain.product.entity.Product;
import com.cakeshop.domain.product.entity.ProductStatus;
import com.cakeshop.domain.product.entity.ProductType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 관리자 상품 등록·수정 입력 전용 DTO. 이미지는 MultipartFile로 별도 전달한다(store 패턴). */
@Getter
@Setter
public class ProductForm {

    @NotBlank(message = "상품명을 입력해 주세요.")
    @Size(max = 150, message = "상품명은 150자 이하여야 합니다.")
    private String name;

    @NotNull(message = "상품 유형을 선택해 주세요.")
    private ProductType productType;

    @NotNull(message = "판매 가격을 입력해 주세요.")
    @Min(value = 0, message = "판매 가격은 0원 이상이어야 합니다.")
    @Max(value = 99_999_999, message = "판매 가격이 너무 큽니다.")
    private Long basePrice;

    // 주문제작(CUSTOM)은 재고를 관리하지 않아 비워 둔다. 그 외 유형은 필수(교차 검증).
    @Min(value = 0, message = "재고는 0개 이상이어야 합니다.")
    @Max(value = 99_999, message = "재고가 너무 큽니다.")
    private Integer stockQuantity;

    @NotNull(message = "픽업 준비일을 입력해 주세요.")
    @Min(value = 0, message = "픽업 준비일은 0일 이상이어야 합니다.")
    @Max(value = 60, message = "픽업 준비일은 60일 이하여야 합니다.")
    private Integer preparationDays = 0;

    @Size(max = 2000, message = "상품 설명은 2,000자 이하여야 합니다.")
    private String description;

    @NotNull(message = "판매 상태를 선택해 주세요.")
    private ProductStatus status = ProductStatus.ACTIVE;

    public static ProductForm from(Product product) {
        ProductForm form = new ProductForm();
        form.setName(product.getName());
        form.setProductType(ProductType.valueOf(product.getProductType()));
        form.setBasePrice(product.getBasePrice());
        form.setStockQuantity(product.getStockQuantity());
        form.setPreparationDays(product.getPreparationDays());
        form.setDescription(product.getDescription());
        form.setStatus(ProductStatus.valueOf(product.getStatus()));
        return form;
    }

    // 재고 관리 유형인데 재고가 비어 있으면 반려한다. (CUSTOM은 재고 없음이 정상)
    @AssertTrue(message = "재고 수량을 입력해 주세요. (주문 제작 상품만 재고 없이 등록할 수 있습니다)")
    public boolean isStockProvided() {
        return productType == null || !productType.isStockManaged() || stockQuantity != null;
    }
}
