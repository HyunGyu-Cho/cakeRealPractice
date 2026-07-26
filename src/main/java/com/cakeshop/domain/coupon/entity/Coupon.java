package com.cakeshop.domain.coupon.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 쿠폰 캠페인 1건. 금액은 전부 원 단위 정수로 다룬다
 * ({@code discount_value}만 DECIMAL(12,2)이지만 정률·정액 모두 정수로만 입력받는다).
 */
@Getter
@Setter
public class Coupon {
    private Long id;
    private String name;
    private String discountType;
    private Long discountValue;
    private Long minimumOrderAmount;
    private Long maximumDiscountAmount;
    private Integer totalQuantity;
    private Integer issuedQuantity;
    private LocalDateTime startsAt;
    private LocalDateTime expiresAt;
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
