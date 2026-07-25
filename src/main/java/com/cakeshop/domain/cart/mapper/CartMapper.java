package com.cakeshop.domain.cart.mapper;

import com.cakeshop.domain.cart.entity.CartItem;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CartMapper {

    int insertCartIfAbsent(@Param("memberId") Long memberId);

    Optional<Long> findCartIdByMemberIdForUpdate(@Param("memberId") Long memberId);

    Optional<CartItem> findItemByCartAndProduct(
        @Param("cartId") Long cartId, @Param("productId") Long productId);

    Optional<CartItem> findItemByIdAndCart(
        @Param("cartItemId") Long cartItemId, @Param("cartId") Long cartId);

    List<CartItem> findItemsByMemberId(@Param("memberId") Long memberId);

    List<CartItem> findItemsByIdsAndMemberId(
        @Param("memberId") Long memberId, @Param("cartItemIds") List<Long> cartItemIds);

    int countItemsByIdsAndMemberId(
        @Param("memberId") Long memberId, @Param("cartItemIds") List<Long> cartItemIds);

    int sumQuantityByMemberId(@Param("memberId") Long memberId);

    int insertItem(CartItem cartItem);

    int updateItemQuantity(
        @Param("cartItemId") Long cartItemId, @Param("quantity") int quantity);

    int deleteItemByIdAndMemberId(
        @Param("cartItemId") Long cartItemId, @Param("memberId") Long memberId);

    int deleteItemsByIdsAndMemberId(
        @Param("memberId") Long memberId, @Param("cartItemIds") List<Long> cartItemIds);

    int deleteAllByMemberId(@Param("memberId") Long memberId);
}
