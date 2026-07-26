package com.cakeshop.domain.coupon.entity;

/**
 * 할인 유형. <b>상태가 아니라 종류</b>다(conventions.md 분류).
 *
 * <p>할인액 계산을 여기에 두는 이유는 {@code OrderStatus.canTransitionTo()}가 전이를 소유하는 것과 같다 —
 * 유형이 늘어날 때 고쳐야 할 자리를 한 곳으로 묶는다.
 */
public enum DiscountType {

    PERCENTAGE("정률 할인"),
    FIXED_AMOUNT("정액 할인");

    private final String label;

    DiscountType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * 원가에 적용될 할인액. 정률은 최대 할인액으로 상한을 두고, 어느 유형이든 원가를 넘지 않게 자른다.
     * 결과가 원가를 넘으면 최종 결제 금액이 음수가 되기 때문이다.
     *
     * @param originalAmount 원가(원)
     * @param discountValue  정률이면 퍼센트, 정액이면 금액
     * @param maximumDiscountAmount 정률의 최대 할인액. {@code null}이면 상한 없음
     */
    public long calculate(long originalAmount, long discountValue, Long maximumDiscountAmount) {
        long discount = switch (this) {
            case PERCENTAGE -> originalAmount * discountValue / 100;  // 원 단위 절사
            case FIXED_AMOUNT -> discountValue;
        };
        if (this == PERCENTAGE && maximumDiscountAmount != null) {
            discount = Math.min(discount, maximumDiscountAmount);
        }
        return Math.max(0, Math.min(discount, originalAmount));
    }

    /** 화면에 쓰는 할인 표기. 저장하지 않는 파생 라벨이다. */
    public String describe(long discountValue, Long maximumDiscountAmount) {
        if (this == FIXED_AMOUNT) {
            return String.format("%,d원 할인", discountValue);
        }
        if (maximumDiscountAmount == null) {
            return discountValue + "% 할인";
        }
        return String.format("%d%% 할인 (최대 %,d원)", discountValue, maximumDiscountAmount);
    }
}
