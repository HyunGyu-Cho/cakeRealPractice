package com.cakeshop.domain.cart.dto.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CartUpdateForm {

    @NotNull(message = "수량을 입력해 주세요.")
    @Positive(message = "수량은 1개 이상이어야 합니다.")
    private Integer quantity;
}
