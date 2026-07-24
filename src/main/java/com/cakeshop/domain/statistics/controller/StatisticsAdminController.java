package com.cakeshop.domain.statistics.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StatisticsAdminController {

    // 데이터 집계 Service가 완성되기 전에는 샘플 화면만 반환한다.
    @GetMapping("/admin")
    public String dashboard() {
        return "admin/dashboard";
    }

    @GetMapping("/admin/statistics")
    public String statistics() {
        return "admin/statistics";
    }
}
