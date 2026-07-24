package com.cakeshop.domain.home.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.home.service.HomeService;
import com.cakeshop.domain.store.dto.view.StorePublicView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HomeControllerTests {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        HomeService homeService = mock(HomeService.class);
        when(homeService.getStore()).thenReturn(new StorePublicView(
            "스위트온 케이크", "소개", "/uploads/store/202607/photo.jpg", "서울시", "02-0000-0000",
            "평일 10:00 ~ 20:00", "일요일 휴무", "1층", "10:00 ~ 19:00"
        ));
        mockMvc = MockMvcBuilders.standaloneSetup(new HomeController(homeService)).build();
    }

    @Test
    void homeReturnsMainTemplate() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(view().name("home/main"));
    }
}
