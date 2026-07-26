package com.cakeshop.domain.order.mapper;

import com.cakeshop.domain.order.entity.CustomOrderPaymentLink;
import com.cakeshop.domain.order.entity.CustomOrderQuote;
import com.cakeshop.domain.order.entity.OrderItemImage;
import com.cakeshop.domain.order.entity.OrderItemOption;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 주문제작 전용 SQL. orders·order_items 본체는 {@link OrderMapper}가 담당한다. */
@Mapper
public interface CustomOrderMapper {

    // ---- 요청서 하위 행 ----
    int insertOrderItemOption(OrderItemOption option);

    int insertOrderItemImage(OrderItemImage image);

    List<OrderItemOption> findOptionsByOrderItemId(@Param("orderItemId") Long orderItemId);

    List<OrderItemImage> findImagesByOrderItemId(@Param("orderItemId") Long orderItemId);

    // ---- 견적 ----
    int insertQuote(CustomOrderQuote quote);

    List<CustomOrderQuote> findQuotesByOrderId(@Param("orderId") Long orderId);

    Optional<CustomOrderQuote> findQuoteById(@Param("quoteId") Long quoteId);

    /** 최신 회차 한 건. 재견적 회차 계산과 수락 대상 판정에 쓴다. */
    Optional<CustomOrderQuote> findLatestQuote(@Param("orderId") Long orderId);

    Optional<CustomOrderQuote> findLatestQuoteForUpdate(@Param("orderId") Long orderId);

    List<CustomOrderQuote> findLatestQuotesByOrderIds(@Param("orderIds") Collection<Long> orderIds);

    /** 상태 조건부 전이. 기대 상태가 아니면 0을 반환해 동시 요청을 막는다. */
    int updateQuoteStatus(@Param("quoteId") Long quoteId,
                          @Param("currentStatus") String currentStatus,
                          @Param("nextStatus") String nextStatus,
                          @Param("acceptedAt") LocalDateTime acceptedAt);

    /** 재견적 시 남아 있는 SENT 회차를 한 번에 밀어낸다. */
    int supersedeSentQuotes(@Param("orderId") Long orderId);

    // ---- 결제 링크 ----
    int insertPaymentLink(CustomOrderPaymentLink link);

    Optional<CustomOrderPaymentLink> findLinkByToken(@Param("token") String token);

    /** 결제 트랜잭션 진입점 — 링크 행을 잠근 뒤 상태를 다시 확인한다. */
    Optional<CustomOrderPaymentLink> findLinkByTokenForUpdate(@Param("token") String token);

    Optional<CustomOrderPaymentLink> findLinkByQuoteId(@Param("quoteId") Long quoteId);

    int updateLinkStatus(@Param("linkId") Long linkId,
                         @Param("currentStatus") String currentStatus,
                         @Param("nextStatus") String nextStatus,
                         @Param("at") LocalDateTime at);

    /** 재견적·취소 시 해당 주문의 살아 있는 링크를 모두 회수한다. */
    int revokeIssuedLinks(@Param("orderId") Long orderId, @Param("at") LocalDateTime at);

    // ---- 관리자 목록 ----
    long countCustomOrders(@Param("status") String status);

    List<Long> findCustomOrderIdPage(@Param("status") String status,
                                     @Param("size") int size,
                                     @Param("offset") int offset);

    // ---- 고객 진입점 ----
    Optional<Long> findLatestOrderIdByMemberId(@Param("memberId") Long memberId);

    /** 주문의 대표(유일) 주문제작 항목 id. 요청서는 항상 항목 1행이다. */
    Optional<Long> findCustomOrderItemId(@Param("orderId") Long orderId);

    int updateFinalAmount(@Param("orderId") Long orderId, @Param("finalAmount") Long finalAmount);

    int updatePickupAt(@Param("orderId") Long orderId, @Param("pickupAt") LocalDateTime pickupAt);

    int reject(@Param("orderId") Long orderId,
               @Param("currentStatus") String currentStatus,
               @Param("reason") String reason);
}
