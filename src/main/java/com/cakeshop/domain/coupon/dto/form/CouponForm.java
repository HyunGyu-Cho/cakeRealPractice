package com.cakeshop.domain.coupon.dto.form;

import com.cakeshop.domain.coupon.entity.Coupon;
import com.cakeshop.domain.coupon.entity.DiscountType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/** 관리자 쿠폰 등록·수정 입력 전용 DTO. 금액은 모두 원 단위 정수다. */
@Getter
@Setter
public class CouponForm {

    @NotBlank(message = "쿠폰명을 입력해 주세요.")
    @Size(max = 100, message = "쿠폰명은 100자 이하여야 합니다.")
    private String name;

    @NotNull(message = "할인 유형을 선택해 주세요.")
    private DiscountType discountType;

    @NotNull(message = "할인 값을 입력해 주세요.")
    @Min(value = 1, message = "할인 값은 1 이상이어야 합니다.")
    @Max(value = 9_999_999, message = "할인 값이 너무 큽니다.")
    private Long discountValue;

    @NotNull(message = "최소 주문 금액을 입력해 주세요.")
    @Min(value = 0, message = "최소 주문 금액은 0원 이상이어야 합니다.")
    @Max(value = 99_999_999, message = "최소 주문 금액이 너무 큽니다.")
    private Long minimumOrderAmount = 0L;

    // 정률 할인에서만 의미가 있다. 정액은 비워 둔다(교차 검증).
    @Min(value = 1, message = "최대 할인 금액은 1원 이상이어야 합니다.")
    @Max(value = 99_999_999, message = "최대 할인 금액이 너무 큽니다.")
    private Long maximumDiscountAmount;

    @NotNull(message = "발급 수량을 입력해 주세요.")
    @Min(value = 1, message = "발급 수량은 1장 이상이어야 합니다.")
    @Max(value = 1_000_000, message = "발급 수량이 너무 큽니다.")
    private Integer totalQuantity;

    @NotNull(message = "발급 시작 일시를 입력해 주세요.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startsAt;

    @NotNull(message = "만료 일시를 입력해 주세요.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime expiresAt;

    public static CouponForm from(Coupon coupon) {
        CouponForm form = new CouponForm();
        form.setName(coupon.getName());
        form.setDiscountType(DiscountType.valueOf(coupon.getDiscountType()));
        form.setDiscountValue(coupon.getDiscountValue());
        form.setMinimumOrderAmount(coupon.getMinimumOrderAmount());
        form.setMaximumDiscountAmount(coupon.getMaximumDiscountAmount());
        form.setTotalQuantity(coupon.getTotalQuantity());
        form.setStartsAt(coupon.getStartsAt());
        form.setExpiresAt(coupon.getExpiresAt());
        return form;
    }

    @AssertTrue(message = "만료 일시는 발급 시작 일시보다 뒤여야 합니다.")
    public boolean isPeriodValid() {
        return startsAt == null || expiresAt == null || expiresAt.isAfter(startsAt);
    }

    @AssertTrue(message = "정률 할인율은 1~100 사이여야 합니다.")
    public boolean isPercentageInRange() {
        return discountType != DiscountType.PERCENTAGE
            || discountValue == null
            || (discountValue >= 1 && discountValue <= 100);
    }

    // 정액 할인에 최대 할인 금액을 함께 넣으면 어느 쪽이 적용되는지 화면에서 읽을 수 없다.
    @AssertTrue(message = "최대 할인 금액은 정률 할인에만 설정할 수 있습니다.")
    public boolean isMaximumDiscountAllowed() {
        return discountType != DiscountType.FIXED_AMOUNT || maximumDiscountAmount == null;
    }
}
