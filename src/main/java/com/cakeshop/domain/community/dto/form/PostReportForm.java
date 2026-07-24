package com.cakeshop.domain.community.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PostReportForm {

    @NotBlank(message = "신고 사유를 입력해 주세요.")
    @Size(max = 500, message = "신고 사유는 500자 이하여야 합니다.")
    private String reason;
}
