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
