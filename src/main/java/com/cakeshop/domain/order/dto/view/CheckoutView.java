package com.cakeshop.domain.order.dto.view;

import com.cakeshop.domain.coupon.dto.view.AvailableCouponView;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 체크아웃·결제 화면이 쓰는 금액 묶음.
 *
 * <p>{@code discountAmount}·{@code finalAmount}는 서버가 쿠폰으로 계산한 값이다. 화면이 보낸 금액은
 * 쓰지 않으며, 결제 트랜잭션에서 같은 계산을 다시 한다.
 */
public record CheckoutView(
    String checkoutId,
    List<CheckoutItemView> items,
    long totalAmount,
    int maximumPreparationDays,
    LocalDateTime pickupAt,
    List<AvailableCouponView> availableCoupons,
    Long selectedMemberCouponId,
    long discountAmount,
    long finalAmount
) {
    /** 쿠폰을 아직 붙이지 않은 화면(픽업 설정 등)용. */
    public CheckoutView(String checkoutId, List<CheckoutItemView> items, long totalAmount,
                        int maximumPreparationDays, LocalDateTime pickupAt) {
        this(checkoutId, items, totalAmount, maximumPreparationDays, pickupAt,
            List.of(), null, 0L, totalAmount);
    }
}
