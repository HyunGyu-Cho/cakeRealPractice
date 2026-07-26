package com.cakeshop.domain.coupon.dto.view;

import java.time.LocalDateTime;

/**
 * 다운로드 가능 쿠폰 목록의 한 줄.
 *
 * <p>{@code downloadable}과 {@code unavailableReason}은 기간·정원·보유 여부에서 나오는 파생값이다.
 * 이미 받은 쿠폰도 목록에는 남겨 "받음"으로 보여준다.
 */
public record DownloadableCouponView(
    Long couponId,
    String name,
    String discountLabel,
    long minimumOrderAmount,
    LocalDateTime expiresAt,
    int remainingQuantity,
    boolean owned,
    boolean downloadable,
    String unavailableReason) {
}
