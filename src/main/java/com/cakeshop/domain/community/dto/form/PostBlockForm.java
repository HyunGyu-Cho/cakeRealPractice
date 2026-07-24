package com.cakeshop.domain.community.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PostBlockForm {

    @NotBlank(message = "제재 사유를 입력해 주세요.")
    @Size(max = 500, message = "제재 사유는 500자 이하여야 합니다.")
    private String reason;
}
