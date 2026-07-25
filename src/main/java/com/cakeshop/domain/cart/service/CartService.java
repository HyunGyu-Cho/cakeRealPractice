package com.cakeshop.domain.cart.service;

import com.cakeshop.domain.cart.dto.view.CartItemView;
import com.cakeshop.domain.cart.dto.view.CartView;
import com.cakeshop.domain.cart.dto.view.CheckoutCartItem;
import com.cakeshop.domain.cart.entity.CartItem;
import com.cakeshop.domain.cart.error.CartErrorCode;
import com.cakeshop.domain.cart.mapper.CartMapper;
import com.cakeshop.domain.product.dto.view.ProductDetailView;
import com.cakeshop.domain.product.dto.view.ProductSalesInfo;
import com.cakeshop.domain.product.entity.ProductType;
import com.cakeshop.domain.product.service.ProductService;
import com.cakeshop.global.error.BusinessException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {

    private final CartMapper cartMapper;
    private final ProductService productService;

    public CartService(CartMapper cartMapper, ProductService productService) {
        this.cartMapper = cartMapper;
        this.productService = productService;
    }

    @Transactional(readOnly = true)
    public CartView getCart(Long memberId) {
        List<CartItemView> items = cartMapper.findItemsByMemberId(memberId).stream()
            .map(this::toView)
            .toList();
        int totalQuantity = items.stream().mapToInt(CartItemView::quantity).sum();
        long totalAmount = items.stream().mapToLong(CartItemView::currentTotalPrice).sum();
        return new CartView(items, totalQuantity, totalAmount);
    }

    @Transactional(readOnly = true)
    public int getTotalQuantity(Long memberId) {
        return cartMapper.sumQuantityByMemberId(memberId);
    }

    @Transactional
    public void addItem(Long memberId, Long productId, int quantity) {
        validatePositive(quantity);
        ProductDetailView detail = productService.getProductDetail(productId);
        validateGeneralProduct(detail);
        validateQuantity(productService.getSalesInfo(productId), quantity);

        // INSERT IGNORE + 회원 UNIQUE로 최초 동시 생성도 한 행으로 수렴시킨 뒤 그 행을 잠근다.
        cartMapper.insertCartIfAbsent(memberId);
        Long cartId = cartMapper.findCartIdByMemberIdForUpdate(memberId)
            .orElseThrow(() -> new BusinessException(CartErrorCode.ITEM_NOT_FOUND));

        CartItem existing = cartMapper.findItemByCartAndProduct(cartId, productId).orElse(null);
        long desiredQuantity = (long) quantity + (existing == null ? 0 : existing.getQuantity());
        if (desiredQuantity > Integer.MAX_VALUE) {
            throw new BusinessException(CartErrorCode.STOCK_EXCEEDED);
        }

        // 잠금을 얻은 뒤 다시 읽어 같은 회원의 동시 요청이 현재 재고를 합산 초과하지 않게 한다.
        validateQuantity(productService.getSalesInfo(productId), (int) desiredQuantity);
        if (existing == null) {
            CartItem item = new CartItem();
            item.setCartId(cartId);
            item.setProductId(productId);
            item.setQuantity((int) desiredQuantity);
            cartMapper.insertItem(item);
        } else {
            cartMapper.updateItemQuantity(existing.getId(), (int) desiredQuantity);
        }
    }

    @Transactional
    public void updateQuantity(Long memberId, Long cartItemId, int quantity) {
        validatePositive(quantity);
        Long cartId = cartMapper.findCartIdByMemberIdForUpdate(memberId)
            .orElseThrow(() -> new BusinessException(CartErrorCode.ITEM_NOT_FOUND));
        CartItem item = cartMapper.findItemByIdAndCart(cartItemId, cartId)
            .orElseThrow(() -> new BusinessException(CartErrorCode.ITEM_NOT_FOUND));

        ProductDetailView detail = productService.getProductDetail(item.getProductId());
        validateGeneralProduct(detail);
        validateQuantity(productService.getSalesInfo(item.getProductId()), quantity);
        cartMapper.updateItemQuantity(cartItemId, quantity);
    }

    @Transactional
    public void deleteItem(Long memberId, Long cartItemId) {
        if (cartMapper.deleteItemByIdAndMemberId(cartItemId, memberId) == 0) {
            throw new BusinessException(CartErrorCode.ITEM_NOT_FOUND);
        }
    }

    @Transactional
    public void deleteSelected(Long memberId, List<Long> cartItemIds) {
        List<Long> ids = normalizeSelection(cartItemIds);
        verifyOwnership(memberId, ids);
        cartMapper.deleteItemsByIdsAndMemberId(memberId, ids);
    }

    @Transactional
    public void deleteAll(Long memberId) {
        // 장바구니 행은 유지하고 항목만 제거한다.
        cartMapper.deleteAllByMemberId(memberId);
    }

    /**
     * order 공개 계약. 소유권과 최신 판매 여부·가격·재고를 호출할 때마다 다시 확인한다.
     */
    @Transactional(readOnly = true)
    public List<CheckoutCartItem> getCheckoutItems(Long memberId, List<Long> cartItemIds) {
        List<Long> ids = normalizeSelection(cartItemIds);
        List<CartItem> items = cartMapper.findItemsByIdsAndMemberId(memberId, ids);
        if (items.size() != ids.size()) {
            throw new BusinessException(CartErrorCode.ITEM_NOT_FOUND);
        }

        Map<Long, CheckoutCartItem> validated = new LinkedHashMap<>();
        for (CartItem item : items) {
            ProductDetailView detail = productService.getProductDetail(item.getProductId());
            validateGeneralProduct(detail);
            ProductSalesInfo salesInfo = productService.getSalesInfo(item.getProductId());
            validateQuantity(salesInfo, item.getQuantity());
            validated.put(item.getId(), new CheckoutCartItem(
                item.getId(), item.getProductId(), item.getQuantity(), salesInfo.price()));
        }
        return ids.stream().map(validated::get).toList();
    }

    private CartItemView toView(CartItem item) {
        ProductDetailView product = productService.getProductDetail(item.getProductId());
        ProductSalesInfo salesInfo = productService.getSalesInfo(item.getProductId());
        String unavailableReason = unavailableReason(product, salesInfo, item.getQuantity());
        long totalPrice = Math.multiplyExact(salesInfo.price(), item.getQuantity().longValue());
        return new CartItemView(
            item.getId(), item.getProductId(), product.name(), product.imageUrl(),
            product.productType(), product.productTypeLabel(), item.getQuantity(),
            salesInfo.price(), totalPrice, salesInfo.stockQuantity(),
            unavailableReason == null, unavailableReason
        );
    }

    private String unavailableReason(ProductDetailView product,
                                     ProductSalesInfo salesInfo,
                                     int quantity) {
        if (ProductType.CUSTOM.name().equals(product.productType())) {
            return "주문 제작 상품은 장바구니에서 주문할 수 없습니다.";
        }
        if (!"ACTIVE".equals(product.status())) {
            return "판매가 중지된 상품입니다.";
        }
        if (!salesInfo.onSale()) {
            return "품절된 상품입니다.";
        }
        if (salesInfo.stockQuantity() == null || quantity > salesInfo.stockQuantity()) {
            return "재고가 부족합니다. 현재 재고: "
                + (salesInfo.stockQuantity() == null ? 0 : salesInfo.stockQuantity()) + "개";
        }
        return null;
    }

    private void validateGeneralProduct(ProductDetailView product) {
        if (ProductType.CUSTOM.name().equals(product.productType())) {
            throw new BusinessException(CartErrorCode.CUSTOM_PRODUCT_NOT_ALLOWED);
        }
    }

    private void validateQuantity(ProductSalesInfo salesInfo, int quantity) {
        validatePositive(quantity);
        if (!salesInfo.onSale()) {
            throw new BusinessException(CartErrorCode.PRODUCT_NOT_ON_SALE);
        }
        if (salesInfo.stockQuantity() == null || quantity > salesInfo.stockQuantity()) {
            throw new BusinessException(CartErrorCode.STOCK_EXCEEDED);
        }
    }

    private void validatePositive(int quantity) {
        if (quantity < 1) {
            throw new BusinessException(CartErrorCode.INVALID_QUANTITY);
        }
    }

    private List<Long> normalizeSelection(List<Long> cartItemIds) {
        if (cartItemIds == null) {
            throw new BusinessException(CartErrorCode.EMPTY_SELECTION);
        }
        List<Long> ids = new LinkedHashSet<>(cartItemIds).stream()
            .filter(id -> id != null && id > 0)
            .toList();
        if (ids.isEmpty()) {
            throw new BusinessException(CartErrorCode.EMPTY_SELECTION);
        }
        return ids;
    }

    private void verifyOwnership(Long memberId, List<Long> ids) {
        if (cartMapper.countItemsByIdsAndMemberId(memberId, ids) != ids.size()) {
            throw new BusinessException(CartErrorCode.ITEM_NOT_FOUND);
        }
    }
}
