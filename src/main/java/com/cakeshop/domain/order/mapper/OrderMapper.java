package com.cakeshop.domain.order.mapper;

import com.cakeshop.domain.order.dto.form.OrderSearchForm;
import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderItem;
import java.time.LocalDate;
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

    int updateStatus(@Param("orderId") Long orderId,
                     @Param("currentStatus") String currentStatus,
                     @Param("nextStatus") String nextStatus);

    int cancelOrder(@Param("orderId") Long orderId,
                    @Param("currentStatus") String currentStatus,
                    @Param("reason") String reason,
                    @Param("canceledBy") String canceledBy);
}
