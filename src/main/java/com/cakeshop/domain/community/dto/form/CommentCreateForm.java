package com.cakeshop.domain.community.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommentCreateForm {

    @NotBlank(message = "댓글 내용을 입력해 주세요.")
    @Size(max = 1000, message = "댓글은 1,000자 이하여야 합니다.")
    private String content;

    // 답글일 때만 값이 있다. 화면은 1단계 답글까지만 허용한다.
    private Long parentCommentId;
}
