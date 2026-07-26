package com.cakeshop.domain.order.dto.session;

import com.cakeshop.domain.order.dto.form.GeneralOrderForm;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CheckoutDraft implements Serializable {
    public static final String SESSION_ATTRIBUTE = "generalOrderCheckout";

    private final String checkoutId;
    private final Long memberId;
    private final List<Long> cartItemIds;
    private LocalDateTime pickupAt;
    private String ordererName;
    private String ordererPhone;
    private String pickupName;
    private String pickupPhone;
    private String requestMessage;
    /**
     * 고객이 고른 쿠폰. <b>id만</b> 담고 할인 금액은 담지 않는다 — 금액은 결제 시점에 서버가 다시 계산한다.
     * 초안 단계에서는 쿠폰을 잡아두지 않으므로, 결제까지 가지 않은 초안이 쿠폰을 묶지 않는다.
     */
    private Long memberCouponId;

    public CheckoutDraft(Long memberId, List<Long> cartItemIds) {
        this.checkoutId = UUID.randomUUID().toString();
        this.memberId = memberId;
        this.cartItemIds = List.copyOf(cartItemIds);
    }

    public void updateOrderer(GeneralOrderForm form) {
        ordererName = form.getOrdererName().trim();
        ordererPhone = form.getOrdererPhone().trim();
        pickupName = form.getPickupName().trim();
        pickupPhone = form.getPickupPhone().trim();
        requestMessage = form.getRequestMessage() == null || form.getRequestMessage().isBlank()
            ? null : form.getRequestMessage().trim();
    }

    public boolean isOrdererComplete() {
        return ordererName != null && ordererPhone != null
            && pickupName != null && pickupPhone != null;
    }
}
