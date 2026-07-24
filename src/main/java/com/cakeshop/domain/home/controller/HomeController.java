package com.cakeshop.domain.home.controller;

import com.cakeshop.domain.home.service.HomeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("store", homeService.getStore());
        return "home/main";
    }

    @GetMapping("/screens")
    public String screens() {
        return "home/screens";
    }
}
