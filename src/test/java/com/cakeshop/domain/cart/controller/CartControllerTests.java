package com.cakeshop.domain.cart.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.cart.dto.view.CartView;
import com.cakeshop.domain.cart.dto.view.CheckoutCartItem;
import com.cakeshop.domain.cart.error.CartErrorCode;
import com.cakeshop.domain.cart.service.CartService;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.GlobalExceptionHandler;
import com.cakeshop.global.security.MemberDetails;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
class CartControllerTests {

    private static final MemberDetails MEMBER = new MemberDetails(
        1L, "user@cakeshop.local", "hash", List.of(new SimpleGrantedAuthority("ROLE_USER")));

    @Mock
    private CartService cartService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        HandlerMethodArgumentResolver principalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType() == MemberDetails.class;
            }

            @Override
            public Object resolveArgument(MethodParameter parameter,
                                          ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
                return MEMBER;
            }
        };
        mockMvc = MockMvcBuilders.standaloneSetup(new CartController(cartService))
            .setCustomArgumentResolvers(principalResolver)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void cartLoadsMembersDatabaseCart() throws Exception {
        when(cartService.getCart(1L)).thenReturn(new CartView(List.of(), 0, 0));

        mockMvc.perform(get("/cart"))
            .andExpect(status().isOk())
            .andExpect(view().name("customer/cart/list"))
            .andExpect(model().attributeExists("cart"));
    }

    @Test
    void validAddUsesPrgAndSuccessMessage() throws Exception {
        mockMvc.perform(post("/cart/items")
                .param("productId", "7")
                .param("quantity", "12"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/cart"))
            .andExpect(flash().attribute("successMessage", "장바구니에 상품을 담았습니다."));

        verify(cartService).addItem(1L, 7L, 12);
    }

    @Test
    void invalidQuantityRedirectsWithErrorWithoutServiceCall() throws Exception {
        mockMvc.perform(post("/cart/items")
                .param("productId", "7")
                .param("quantity", "0"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/products/7"))
            .andExpect(flash().attributeExists("errorMessage"));

        verify(cartService, never()).addItem(any(), any(), any(Integer.class));
    }

    @Test
    void updateStockErrorReturnsToCartWithFlashMessage() throws Exception {
        doThrow(new BusinessException(CartErrorCode.STOCK_EXCEEDED))
            .when(cartService).updateQuantity(1L, 3L, 9);

        mockMvc.perform(post("/cart/items/3/quantity").param("quantity", "9"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/cart"))
            .andExpect(flash().attribute("errorMessage", CartErrorCode.STOCK_EXCEEDED.message()));
    }

    @Test
    void missingSelectionReturnsToCart() throws Exception {
        doThrow(new BusinessException(CartErrorCode.EMPTY_SELECTION))
            .when(cartService).getCheckoutItems(eq(1L), any());

        mockMvc.perform(post("/cart/checkout"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/cart"))
            .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void checkoutPassesSelectedIdsToPickupStep() throws Exception {
        when(cartService.getCheckoutItems(1L, List.of(3L, 4L))).thenReturn(List.of(
            new CheckoutCartItem(3L, 7L, 1, 35000L),
            new CheckoutCartItem(4L, 8L, 2, 42000L)));

        mockMvc.perform(post("/cart/checkout").param("itemIds", "3", "4"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/orders/pickup?cartItemIds=3&cartItemIds=4"));
    }

    @Test
    void anotherMembersItemReturnsNotFound() throws Exception {
        doThrow(new BusinessException(CartErrorCode.ITEM_NOT_FOUND))
            .when(cartService).deleteItem(1L, 99L);

        mockMvc.perform(post("/cart/items/99/delete"))
            .andExpect(status().isNotFound())
            .andExpect(view().name("error/404"));
    }
}
