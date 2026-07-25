package com.cakeshop.domain.order.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GeneralOrderForm {
    @NotBlank(message = "주문자 이름을 입력해 주세요.")
    @Size(max = 50)
    private String ordererName;

    @NotBlank(message = "주문자 전화번호를 입력해 주세요.")
    @Pattern(regexp = "^[0-9+() -]{8,30}$", message = "주문자 전화번호 형식이 올바르지 않습니다.")
    private String ordererPhone;

    @NotBlank(message = "픽업자 이름을 입력해 주세요.")
    @Size(max = 50)
    private String pickupName;

    @NotBlank(message = "픽업자 전화번호를 입력해 주세요.")
    @Pattern(regexp = "^[0-9+() -]{8,30}$", message = "픽업자 전화번호 형식이 올바르지 않습니다.")
    private String pickupPhone;

    @Size(max = 2000)
    private String requestMessage;
}
