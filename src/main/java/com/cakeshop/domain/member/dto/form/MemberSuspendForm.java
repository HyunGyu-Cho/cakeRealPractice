package com.cakeshop.domain.member.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 이용 제한 사유. 해제는 사유를 받지 않으므로 이 폼을 쓰지 않는다. */
@Getter
@Setter
public class MemberSuspendForm {

    @NotBlank(message = "제한 사유를 입력해 주세요.")
    @Size(max = 500, message = "제한 사유는 500자 이하로 입력해 주세요.")
    private String reason;
}
