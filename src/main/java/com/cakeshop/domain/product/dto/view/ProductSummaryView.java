package com.cakeshop.domain.product.dto.view;

import com.cakeshop.domain.product.entity.ProductStatus;
import com.cakeshop.domain.product.entity.ProductType;
import java.time.LocalDateTime;

/** 목록 카드·관리자 목록 행 출력 전용. 재고 라벨은 stock_quantity 파생값이다(저장 금지). */
public record ProductSummaryView(
    Long id,
    String name,
    Long basePrice,
    String productType,
    String productTypeLabel,
    String status,
    String statusLabel,
    Integer stockQuantity,
    String stockLabel,
    boolean soldOut,
    String imageUrl,
    LocalDateTime createdAt
) {

    public static ProductSummaryView from(ProductSummaryRow row) {
        ProductType type = ProductType.valueOf(row.getProductType());
        ProductStatus status = ProductStatus.valueOf(row.getStatus());
        return new ProductSummaryView(
            row.getId(), row.getName(), row.getBasePrice(),
            type.name(), type.label(),
            status.name(), status.label(),
            row.getStockQuantity(), stockLabel(type, row.getStockQuantity()),
            isSoldOut(row.getStockQuantity()),
            row.getImageUrl(), row.getCreatedAt()
        );
    }

    /** 재고 파생 라벨: 주문제작(재고 미관리) → 주문 가능, 0 → 품절, 1~4 → 재고 부족, 그 외 → 재고 있음 */
    public static String stockLabel(ProductType type, Integer stockQuantity) {
        if (!type.isStockManaged() || stockQuantity == null) {
            return "주문 가능";
        }
        if (stockQuantity == 0) {
            return "품절";
        }
        return stockQuantity < 5 ? "재고 부족" : "재고 있음";
    }

    public static boolean isSoldOut(Integer stockQuantity) {
        return stockQuantity != null && stockQuantity == 0;
    }
}
