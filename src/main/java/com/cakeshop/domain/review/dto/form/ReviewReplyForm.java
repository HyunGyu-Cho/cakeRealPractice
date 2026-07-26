package com.cakeshop.domain.review.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 관리자 답글 입력. 후기당 1개라 등록과 수정이 같은 폼을 쓴다. */
@Getter
@Setter
public class ReviewReplyForm {

    @NotBlank(message = "답글 내용을 입력해 주세요.")
    @Size(max = 1000, message = "답글은 1,000자 이하여야 합니다.")
    private String content;
}
