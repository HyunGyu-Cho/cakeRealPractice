package com.cakeshop.domain.home.service;

import com.cakeshop.domain.product.dto.view.ProductSummaryView;
import com.cakeshop.domain.product.service.ProductService;
import com.cakeshop.domain.store.dto.view.StorePublicView;
import com.cakeshop.domain.store.service.StoreService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class HomeService {

    private static final int MAIN_PRODUCT_COUNT = 4;

    private final StoreService storeService;
    private final ProductService productService;

    public HomeService(StoreService storeService, ProductService productService) {
        this.storeService = storeService;
        this.productService = productService;
    }

    // Home은 전용 Mapper를 만들지 않고 각 도메인의 공개 조회 결과만 단방향으로 조합한다.
    public StorePublicView getStore() {
        return storeService.getPublicStore();
    }

    public List<ProductSummaryView> getLatestProducts() {
        return productService.getLatestActiveProducts(MAIN_PRODUCT_COUNT);
    }
}
