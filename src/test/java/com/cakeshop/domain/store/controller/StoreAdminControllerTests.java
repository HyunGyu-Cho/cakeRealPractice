package com.cakeshop.domain.store.controller;

import static org.mockito.ArgumentMatchers.any;
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

import com.cakeshop.domain.store.dto.form.StoreUpdateForm;
import com.cakeshop.domain.store.dto.view.StoreView;
import com.cakeshop.domain.store.service.StoreService;
import com.cakeshop.global.web.FlashMessage;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class StoreAdminControllerTests {

    @Mock
    private StoreService storeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StoreAdminController(storeService)).build();
    }

    @Test
    void formLoadsUpdateFormAndHolidayList() throws Exception {
        when(storeService.getStoreView()).thenReturn(storeView());

        mockMvc.perform(get("/admin/store"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/store/form"))
            .andExpect(model().attributeExists("storeForm", "holidayForm", "dayOptions", "holidays"));
    }

    @Test
    void invalidUpdateRendersSameFormWithoutCallingService() throws Exception {
        when(storeService.getStoreView()).thenReturn(storeView());

        mockMvc.perform(post("/admin/store").param("name", ""))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/store/form"))
            .andExpect(model().attributeHasFieldErrors("storeForm", "name", "address", "phone"));

        verify(storeService, never()).updateStore(any(StoreUpdateForm.class), any());
    }

    @Test
    void validUpdateRedirectsWithFlashMessage() throws Exception {
        mockMvc.perform(post("/admin/store")
                .param("name", "스위트온 케이크")
                .param("description", "예약 케이크 전문점")
                .param("address", "서울시 OO구")
                .param("phone", "02-0000-0000")
                .param("weekdayOpenTime", "10:00")
                .param("weekdayCloseTime", "20:00")
                .param("weekendOpenTime", "11:00")
                .param("weekendCloseTime", "18:00")
                .param("closedDays", "SUNDAY")
                .param("pickupPlace", "1층 카운터")
                .param("pickupStartTime", "10:00")
                .param("pickupEndTime", "19:00")
                .param("pickupIntervalMinutes", "60"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/store"))
            .andExpect(flash().attribute(FlashMessage.SUCCESS, "매장 정보를 저장했습니다."));

        verify(storeService).updateStore(any(StoreUpdateForm.class), any());
    }

    private StoreView storeView() {
        return new StoreView(
            1L, "스위트온 케이크", "소개", "/uploads/store/202607/photo.jpg", "서울시", "02-0000-0000",
            LocalTime.of(10, 0), LocalTime.of(20, 0), LocalTime.of(11, 0), LocalTime.of(18, 0),
            Set.of(), "1층 카운터", LocalTime.of(10, 0), LocalTime.of(19, 0), 60, List.of()
        );
    }
}
