package com.cakeshop.domain.order.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 관리자 반려 입력. 사유는 필수이며 고객 화면에 그대로 노출된다. */
@Getter
@Setter
public class RejectForm {

    @NotBlank(message = "반려 사유를 입력해 주세요.")
    @Size(max = 500, message = "반려 사유는 500자 이내로 입력해 주세요.")
    private String reason;
}
