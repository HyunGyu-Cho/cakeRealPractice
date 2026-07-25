package com.cakeshop.domain.order.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelForm {
    @NotBlank(message = "취소 사유를 입력해 주세요.")
    @Size(max = 500)
    private String reason;
}
