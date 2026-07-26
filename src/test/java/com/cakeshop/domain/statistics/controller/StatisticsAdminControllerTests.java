package com.cakeshop.domain.statistics.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.order.dto.view.OrderStatsView;
import com.cakeshop.domain.payment.dto.view.PaymentStatsView;
import com.cakeshop.domain.statistics.dto.form.StatisticsSearchForm;
import com.cakeshop.domain.statistics.dto.view.DashboardView;
import com.cakeshop.domain.statistics.dto.view.StatisticsReportView;
import com.cakeshop.domain.statistics.dto.view.TrendChartView;
import com.cakeshop.domain.statistics.service.StatisticsService;
import com.cakeshop.global.common.stats.StatsPeriod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class StatisticsAdminControllerTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 20);

    private MockMvc mockMvc;
    private StatisticsService statisticsService;
    private final List<StatisticsSearchForm> capturedForms = new ArrayList<>();

    @BeforeEach
    void setUp() {
        statisticsService = mock(StatisticsService.class);
        when(statisticsService.today()).thenReturn(TODAY);
        when(statisticsService.getDashboard()).thenReturn(dashboard());
        when(statisticsService.getReport(any(StatisticsSearchForm.class)))
            .thenAnswer(invocation -> {
                capturedForms.add(invocation.getArgument(0));
                return report();
            });

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
            .standaloneSetup(new StatisticsAdminController(statisticsService))
            .setValidator(validator)
            .build();
    }

    @Test
    void 대시보드는_오늘_현황을_모델에_담는다() throws Exception {
        mockMvc.perform(get("/admin"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/dashboard"))
            .andExpect(model().attributeExists("dashboard"));
    }

    @Test
    void 기간을_안_주면_최근_30일_일별로_조회한다() throws Exception {
        mockMvc.perform(get("/admin/statistics"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/statistics"))
            .andExpect(model().attributeExists("report"))
            .andExpect(model().attribute("selectedPeriod", StatsPeriod.DAY));

        StatisticsSearchForm used = capturedForms.getFirst();
        assertThat(used.getEndDate()).isEqualTo(TODAY);
        assertThat(used.getStartDate()).isEqualTo(TODAY.minusDays(29));
    }

    @Test
    void 집계_단위와_기간을_그대로_넘긴다() throws Exception {
        mockMvc.perform(get("/admin/statistics")
                .param("startDate", "2026-07-01")
                .param("endDate", "2026-07-15")
                .param("period", "MONTH"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("selectedPeriod", StatsPeriod.MONTH));

        StatisticsSearchForm used = capturedForms.getFirst();
        assertThat(used.getStartDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(used.getEndDate()).isEqualTo(LocalDate.of(2026, 7, 15));
    }

    @Test
    void 시작일이_종료일보다_늦으면_리다이렉트하지_않고_기본_기간으로_재렌더한다() throws Exception {
        mockMvc.perform(get("/admin/statistics")
                .param("startDate", "2026-07-20")
                .param("endDate", "2026-07-01"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/statistics"))
            .andExpect(model().attributeExists("errorMessage"))
            .andExpect(model().attributeExists("report"));

        // 잘못된 기간이 아니라 기본 기간(최근 30일)으로 집계했다.
        assertThat(capturedForms.getFirst().getStartDate()).isEqualTo(TODAY.minusDays(29));
    }

    @Test
    void 최대_조회_범위를_넘기면_오류로_막는다() throws Exception {
        mockMvc.perform(get("/admin/statistics")
                .param("startDate", "2024-01-01")
                .param("endDate", "2026-07-20"))
            .andExpect(status().isOk())
            .andExpect(model().attributeExists("errorMessage"));
    }

    private DashboardView dashboard() {
        return new DashboardView(0, 0, 0, 0, 0, 0,
            List.of(), List.of(), List.of(), StatisticsService.LOW_STOCK_THRESHOLD);
    }

    private StatisticsReportView report() {
        return new StatisticsReportView(
            OrderStatsView.empty(), PaymentStatsView.empty(), TrendChartView.empty(),
            List.of(), List.of(), 0, 0, BigDecimal.ZERO, 0);
    }
}
