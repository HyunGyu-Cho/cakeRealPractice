package com.cakeshop.domain.order.mapper;

import com.cakeshop.domain.order.dto.form.OrderSearchForm;
import com.cakeshop.domain.order.dto.view.OrderStatsView;
import com.cakeshop.domain.order.dto.view.OrderTrendPointView;
import com.cakeshop.domain.order.dto.view.PickupHourCountView;
import com.cakeshop.domain.order.dto.view.ProductSalesStatsView;
import com.cakeshop.domain.order.dto.view.ReviewableItemView;
import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderItem;
import com.cakeshop.global.common.stats.MemberCountRow;
import com.cakeshop.global.common.stats.StatsPeriod;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrderMapper {
    int insertOrder(Order order);

    int insertOrderItem(OrderItem item);

    Optional<Order> findById(@Param("orderId") Long orderId);

    Optional<Order> findByIdAndMemberId(
        @Param("orderId") Long orderId, @Param("memberId") Long memberId);

    Optional<Order> findByIdForUpdate(@Param("orderId") Long orderId);

    List<OrderItem> findItemsByOrderId(@Param("orderId") Long orderId);

    List<OrderItem> findItemsByOrderIds(@Param("orderIds") Collection<Long> orderIds);

    List<Order> findOngoingByMemberId(@Param("memberId") Long memberId,
                                      @Param("limit") int limit);

    List<Order> findRecentCompletedByMemberId(@Param("memberId") Long memberId,
                                              @Param("limit") int limit);

    long countOrders(@Param("cond") OrderSearchForm cond,
                     @Param("memberIds") Collection<Long> memberIds,
                     @Param("memberFilter") boolean memberFilter);

    List<Order> findOrderPage(@Param("cond") OrderSearchForm cond,
                              @Param("memberIds") Collection<Long> memberIds,
                              @Param("memberFilter") boolean memberFilter,
                              @Param("size") int size,
                              @Param("offset") int offset);

    List<Order> findByIds(@Param("orderIds") Collection<Long> orderIds);

    List<Order> findGeneralOrdersByPickupDate(@Param("pickupDate") LocalDate pickupDate);

    // ---- 후기 작성 자격 (review 도메인이 OrderService 계약으로만 사용한다) ----
    /** 픽업까지 끝난 본인 주문의 항목. 후기를 쓸 자격이 있는 목록이다. */
    List<ReviewableItemView> findPickedUpItemsByMemberId(@Param("memberId") Long memberId);

    Optional<ReviewableItemView> findPickedUpItem(@Param("memberId") Long memberId,
                                                  @Param("orderItemId") Long orderItemId);

    // ---- 픽업 예약 현황 (store의 PickupReservationPort 구현이 사용한다) ----
    /** 취소·반려되지 않은 주문 수. 취소된 주문의 슬롯은 다시 열려야 하므로 제외한다. */
    long countActivePickupsOn(@Param("date") LocalDate date);

    List<LocalDateTime> findActivePickupAts(@Param("dates") Collection<LocalDate> dates);

    // ---- 통계 집계 (statistics 도메인이 OrderStatsService 계약으로만 사용한다) ----
    /** 기간 주문 요약. 매출이 아니라 주문 금액까지만 센다 — payments는 다른 도메인이다. */
    OrderStatsView aggregateOrderStats(@Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);

    List<OrderTrendPointView> aggregateOrderTrend(@Param("from") LocalDateTime from,
                                                  @Param("to") LocalDateTime to,
                                                  @Param("period") StatsPeriod period);

    List<ProductSalesStatsView> aggregateProductSales(@Param("from") LocalDateTime from,
                                                      @Param("to") LocalDateTime to,
                                                      @Param("limit") int limit);

    List<PickupHourCountView> aggregatePickupHours(@Param("from") LocalDateTime from,
                                                   @Param("to") LocalDateTime to);

    List<Order> findActiveOrdersByPickupDate(@Param("date") LocalDate date);

    List<Order> findRecentOrders(@Param("limit") int limit);

    long countByStatus(@Param("status") String status);

    int updateStatus(@Param("orderId") Long orderId,
                     @Param("currentStatus") String currentStatus,
                     @Param("nextStatus") String nextStatus);

    int cancelOrder(@Param("orderId") Long orderId,
                    @Param("currentStatus") String currentStatus,
                    @Param("reason") String reason,
                    @Param("canceledBy") String canceledBy);

    // 회원별 주문 건수 배치 집계 (member 관리자 화면이 공개 계약으로 사용한다).
    // 취소·반려는 제외한다 — statistics 상품별 집계와 같은 기준이다.
    List<MemberCountRow> countOrdersByMemberIds(@Param("memberIds") Collection<Long> memberIds);
}
