package com.cakeshop.domain.statistics.controller;

import com.cakeshop.domain.statistics.dto.form.StatisticsSearchForm;
import com.cakeshop.domain.statistics.service.StatisticsService;
import com.cakeshop.global.common.stats.StatsPeriod;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

/** 관리자 대시보드(오늘 현황)와 통계(기간 집계). 조회 전용이라 POST가 없다. */
@Controller
public class StatisticsAdminController {

    private final StatisticsService statisticsService;

    public StatisticsAdminController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/admin")
    public String dashboard(Model model) {
        model.addAttribute("dashboard", statisticsService.getDashboard());
        return "admin/dashboard";
    }

    /**
     * 기간·집계 단위 조회. 조회 전용이라 검증 실패에도 리다이렉트하지 않고
     * 화면을 다시 그리며, 지표 자리는 기본 기간(최근 30일) 결과로 채운다.
     */
    @GetMapping("/admin/statistics")
    public String statistics(@Valid @ModelAttribute("search") StatisticsSearchForm search,
                             BindingResult bindingResult,
                             Model model) {
        search.applyDefaults(statisticsService.today());
        if (bindingResult.hasErrors()) {
            model.addAttribute("errorMessage",
                bindingResult.getAllErrors().getFirst().getDefaultMessage());
            StatisticsSearchForm fallback = new StatisticsSearchForm();
            fallback.setPeriod(search.getPeriod());
            fallback.applyDefaults(statisticsService.today());
            model.addAttribute("report", statisticsService.getReport(fallback));
        } else {
            model.addAttribute("report", statisticsService.getReport(search));
        }
        model.addAttribute("periods", StatsPeriod.values());
        model.addAttribute("selectedPeriod", search.resolvedPeriod());
        return "admin/statistics";
    }
}
