package com.cakeshop.domain.coupon.dto.form;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** 관리자 지정 발급 입력. 회원은 id로 고른다(닉네임 검색 결과에서 선택). */
@Getter
@Setter
public class CouponIssueForm {

    @NotNull(message = "발급할 회원을 선택해 주세요.")
    private Long memberId;
}
