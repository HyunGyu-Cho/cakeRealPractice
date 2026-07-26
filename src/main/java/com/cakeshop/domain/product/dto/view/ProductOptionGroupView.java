package com.cakeshop.domain.product.dto.view;

import java.util.List;

/**
 * [공개 계약] 상품 옵션 그룹 + 그 그룹의 판매 중(ACTIVE) 옵션 목록.
 * order(수제)가 요청서 화면을 그리고 선택값을 검증할 때 사용한다.
 */
public record ProductOptionGroupView(
    Long id,
    String name,
    boolean required,
    String selectionType,
    List<ProductOptionView> options
) {
}
