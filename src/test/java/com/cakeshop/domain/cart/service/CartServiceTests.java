package com.cakeshop.domain.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.cart.dto.view.CheckoutCartItem;
import com.cakeshop.domain.cart.entity.CartItem;
import com.cakeshop.domain.cart.error.CartErrorCode;
import com.cakeshop.domain.cart.mapper.CartMapper;
import com.cakeshop.domain.product.dto.view.ProductDetailView;
import com.cakeshop.domain.product.dto.view.ProductSalesInfo;
import com.cakeshop.domain.product.service.ProductService;
import com.cakeshop.global.error.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CartServiceTests {

    @Mock
    private CartMapper cartMapper;
    @Mock
    private ProductService productService;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartMapper, productService);
    }

    @Test
    void firstAddCreatesCartAndItemLazily() {
        givenNormalProduct(7L, 20, 35000L, true);
        when(cartMapper.findCartIdByMemberIdForUpdate(1L)).thenReturn(Optional.of(10L));
        when(cartMapper.findItemByCartAndProduct(10L, 7L)).thenReturn(Optional.empty());

        cartService.addItem(1L, 7L, 2);

        verify(cartMapper).insertCartIfAbsent(1L);
        ArgumentCaptor<CartItem> item = ArgumentCaptor.forClass(CartItem.class);
        verify(cartMapper).insertItem(item.capture());
        assertThat(item.getValue().getCartId()).isEqualTo(10L);
        assertThat(item.getValue().getQuantity()).isEqualTo(2);
    }

    @Test
    void sameProductAddsQuantityAndAllowsMoreThanTen() {
        givenNormalProduct(7L, 20, 35000L, true);
        when(cartMapper.findCartIdByMemberIdForUpdate(1L)).thenReturn(Optional.of(10L));
        when(cartMapper.findItemByCartAndProduct(10L, 7L)).thenReturn(Optional.of(item(3L, 7L, 7)));

        cartService.addItem(1L, 7L, 5);

        verify(cartMapper).updateItemQuantity(3L, 12);
        verify(cartMapper, never()).insertItem(any());
    }

    @Test
    void zeroAndStockExcessAreRejected() {
        assertThatCartError(() -> cartService.addItem(1L, 7L, 0), CartErrorCode.INVALID_QUANTITY);

        givenNormalProduct(7L, 5, 35000L, true);
        assertThatCartError(() -> cartService.addItem(1L, 7L, 6), CartErrorCode.STOCK_EXCEEDED);
        verify(cartMapper, never()).insertCartIfAbsent(any());
    }

    @Test
    void inactiveOrSoldOutProductCannotBeAdded() {
        when(productService.getProductDetail(7L)).thenReturn(detail(7L, "GENERAL", "ACTIVE", 0, true));
        when(productService.getSalesInfo(7L)).thenReturn(new ProductSalesInfo(7L, false, 35000L, 0));

        assertThatCartError(() -> cartService.addItem(1L, 7L, 1), CartErrorCode.PRODUCT_NOT_ON_SALE);
    }

    @Test
    void customProductCannotBeAdded() {
        when(productService.getProductDetail(7L))
            .thenReturn(detail(7L, "CUSTOM", "ACTIVE", null, true));

        assertThatCartError(
            () -> cartService.addItem(1L, 7L, 1), CartErrorCode.CUSTOM_PRODUCT_NOT_ALLOWED);
        verify(productService, never()).getSalesInfo(any());
    }

    @Test
    void cartViewUsesLatestPriceAndMarksInsufficientStockInactive() {
        when(cartMapper.findItemsByMemberId(1L)).thenReturn(List.of(item(3L, 7L, 4)));
        when(productService.getProductDetail(7L))
            .thenReturn(detail(7L, "GENERAL", "ACTIVE", 3, true));
        when(productService.getSalesInfo(7L))
            .thenReturn(new ProductSalesInfo(7L, true, 42000L, 3));

        var cart = cartService.getCart(1L);

        assertThat(cart.items()).singleElement().satisfies(view -> {
            assertThat(view.currentUnitPrice()).isEqualTo(42000L);
            assertThat(view.currentTotalPrice()).isEqualTo(168000L);
            assertThat(view.selectable()).isFalse();
            assertThat(view.unavailableReason()).contains("재고가 부족");
        });
    }

    @Test
    void anotherMembersItemLooksNotFound() {
        when(cartMapper.findCartIdByMemberIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatCartError(
            () -> cartService.updateQuantity(1L, 99L, 1), CartErrorCode.ITEM_NOT_FOUND);

        when(cartMapper.deleteItemByIdAndMemberId(99L, 1L)).thenReturn(0);
        assertThatCartError(() -> cartService.deleteItem(1L, 99L), CartErrorCode.ITEM_NOT_FOUND);
    }

    @Test
    void checkoutRevalidatesOwnershipStockAndLatestPrice() {
        CartItem item = item(3L, 7L, 2);
        when(cartMapper.findItemsByIdsAndMemberId(1L, List.of(3L))).thenReturn(List.of(item));
        givenNormalProduct(7L, 5, 41000L, true);

        List<CheckoutCartItem> result = cartService.getCheckoutItems(1L, List.of(3L));

        assertThat(result).containsExactly(new CheckoutCartItem(3L, 7L, 2, 41000L));
    }

    @Test
    void checkoutBlocksStoppedProductAndUnknownOwnership() {
        when(cartMapper.findItemsByIdsAndMemberId(1L, List.of(3L))).thenReturn(List.of());
        assertThatCartError(
            () -> cartService.getCheckoutItems(1L, List.of(3L)), CartErrorCode.ITEM_NOT_FOUND);

        CartItem item = item(4L, 8L, 1);
        when(cartMapper.findItemsByIdsAndMemberId(1L, List.of(4L))).thenReturn(List.of(item));
        when(productService.getProductDetail(8L))
            .thenReturn(detail(8L, "GENERAL", "INACTIVE", 5, false));
        when(productService.getSalesInfo(8L))
            .thenReturn(new ProductSalesInfo(8L, false, 35000L, 5));

        assertThatCartError(
            () -> cartService.getCheckoutItems(1L, List.of(4L)), CartErrorCode.PRODUCT_NOT_ON_SALE);
    }

    @Test
    void emptySelectionIsRejected() {
        assertThatCartError(
            () -> cartService.getCheckoutItems(1L, List.of()), CartErrorCode.EMPTY_SELECTION);
    }

    private void givenNormalProduct(Long productId, int stock, long price, boolean onSale) {
        when(productService.getProductDetail(productId))
            .thenReturn(detail(productId, "GENERAL", "ACTIVE", stock, onSale));
        when(productService.getSalesInfo(productId))
            .thenReturn(new ProductSalesInfo(productId, onSale, price, stock));
    }

    private ProductDetailView detail(Long id, String type, String status,
                                     Integer stock, boolean onSale) {
        return new ProductDetailView(
            id, "딸기 케이크", "설명", 35000L, type,
            "CUSTOM".equals(type) ? "주문 제작" : "일반 케이크",
            status, "ACTIVE".equals(status) ? "판매 중" : "판매 중지",
            stock, stock != null && stock == 0 ? "품절" : "재고 있음",
            0, 0, null, BigDecimal.ZERO, 0, onSale);
    }

    private CartItem item(long id, long productId, int quantity) {
        CartItem item = new CartItem();
        item.setId(id);
        item.setCartId(10L);
        item.setProductId(productId);
        item.setQuantity(quantity);
        return item;
    }

    private void assertThatCartError(Runnable action, CartErrorCode errorCode) {
        assertThatThrownBy(action::run)
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(errorCode);
    }
}
