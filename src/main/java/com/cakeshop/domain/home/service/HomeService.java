package com.cakeshop.domain.home.service;

import com.cakeshop.domain.store.dto.view.StorePublicView;
import com.cakeshop.domain.store.service.StoreService;
import org.springframework.stereotype.Service;

@Service
public class HomeService {

    private final StoreService storeService;

    public HomeService(StoreService storeService) {
        this.storeService = storeService;
    }

    // Home은 전용 Mapper를 만들지 않고 각 도메인의 공개 조회 결과만 단방향으로 조합한다.
    public StorePublicView getStore() {
        return storeService.getPublicStore();
    }
}
