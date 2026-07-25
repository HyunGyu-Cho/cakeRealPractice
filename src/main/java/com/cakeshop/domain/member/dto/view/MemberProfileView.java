package com.cakeshop.domain.member.dto.view;

import java.time.LocalDateTime;

/** [공개 계약 후보] 마이페이지·주문서 등 화면에 노출할 회원 정보만 담는다. 비밀번호·상태는 포함하지 않는다. */
public record MemberProfileView(
    Long id,
    String nickname,
    String email,
    String phone,
    LocalDateTime createdAt
) {
}
