package com.cakeshop.domain.payment.dto.form;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaymentForm {
    @NotBlank(message = "결제 수단을 선택해 주세요.")
    private String method;

    @AssertTrue(message = "취소·환불 규정에 동의해 주세요.")
    private boolean refundPolicyAgreed;

    private Long amount;
}
