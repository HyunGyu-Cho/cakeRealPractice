package com.cakeshop.domain.product.controller;

import com.cakeshop.domain.product.dto.form.ProductSearchForm;
import com.cakeshop.domain.product.service.ProductService;
import com.cakeshop.global.common.paging.PageRequest;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.util.UriUtils;

@Controller
@RequestMapping("/products")
public class ProductController {

    private static final int PAGE_SIZE = 9;

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public String list(@ModelAttribute("search") ProductSearchForm search, Model model) {
        model.addAttribute("pageResult",
            productService.getProductPage(search, new PageRequest(search.getPage(), PAGE_SIZE)));
        // 페이지·정렬 링크에 검색 조건을 유지한다. 한글 검색어는 미리 인코딩한다(community 패턴).
        StringBuilder filterQuery = new StringBuilder();
        appendParam(filterQuery, "type", search.getNormalizedType());
        appendParam(filterQuery, "minPrice", search.getMinPrice());
        appendParam(filterQuery, "maxPrice", search.getMaxPrice());
        appendParam(filterQuery, "sale", search.getNormalizedSale());
        if (search.isPickupTodayOnly()) {
            filterQuery.append("&pickupToday=true");
        }
        if (search.getNormalizedKeyword() != null) {
            filterQuery.append("&keyword=")
                .append(UriUtils.encodeQueryParam(search.getNormalizedKeyword(), StandardCharsets.UTF_8));
        }
        // filterQuery = 정렬 제외(정렬 링크용), extraQuery = 정렬 포함(페이지 링크용)
        model.addAttribute("filterQuery", filterQuery.toString());
        model.addAttribute("currentSort", search.getNormalizedSort());
        model.addAttribute("extraQuery", filterQuery + "&sort=" + search.getNormalizedSort());
        return "customer/product/list";
    }

    @GetMapping("/{productId:\\d+}")
    public String detail(@PathVariable("productId") long productId, Model model) {
        model.addAttribute("product", productService.getProductDetail(productId));
        return "customer/product/detail";
    }

    private void appendParam(StringBuilder query, String name, Object value) {
        if (value != null) {
            query.append('&').append(name).append('=').append(value);
        }
    }
}
