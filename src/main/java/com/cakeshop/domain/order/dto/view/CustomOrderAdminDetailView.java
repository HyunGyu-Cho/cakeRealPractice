package com.cakeshop.domain.order.dto.view;

/**
 * 주문제작 관리자 상세 화면 전용 뷰.
 *
 * <p>요청 내용은 고객용 {@link CustomOrderDetailView}를 그대로 담고, 관리자에게만 필요한
 * 요청 회원 정보를 덧붙인다. 고객 화면에 연락처가 실리지 않도록 고객용 뷰는 건드리지 않는다.
 */
public record CustomOrderAdminDetailView(
    CustomOrderDetailView request,
    Long memberId,
    String memberNickname,
    String memberEmail,
    String memberPhone) {
}
