package com.cakeshop.domain.product.controller;

import com.cakeshop.domain.product.dto.form.ProductSearchForm;
import com.cakeshop.domain.product.service.ProductService;
import com.cakeshop.domain.review.service.ReviewService;
import com.cakeshop.global.common.paging.PageQuery;
import com.cakeshop.global.common.paging.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/products")
public class ProductController {

    private static final int PAGE_SIZE = 9;
    private static final int REVIEW_PAGE_SIZE = 5;

    private final ProductService productService;
    private final ReviewService reviewService;

    /**
     * 후기는 화면 조합 지점인 <b>컨트롤러에서</b> 붙인다. {@code ProductService}가 {@code ReviewService}를
     * 부르면 review → product(집계 갱신)와 맞물려 서비스 간 순환이 된다.
     */
    public ProductController(ProductService productService, ReviewService reviewService) {
        this.productService = productService;
        this.reviewService = reviewService;
    }

    @GetMapping
    public String list(@ModelAttribute("search") ProductSearchForm search, Model model) {
        model.addAttribute("pageResult",
            productService.getProductPage(search, new PageRequest(search.getPage(), PAGE_SIZE)));
        // 페이지·정렬 링크에 검색 조건을 유지한다. 인코딩은 PageQuery가 처리한다(한글 검색어).
        PageQuery filters = PageQuery.of()
            .add("type", search.getNormalizedType())
            .add("minPrice", search.getMinPrice())
            .add("maxPrice", search.getMaxPrice())
            .add("sale", search.getNormalizedSale())
            .add("pickupToday", search.isPickupTodayOnly() ? "true" : null)
            .add("keyword", search.getNormalizedKeyword());
        // filterQuery = 정렬 제외(정렬 링크용), extraQuery = 정렬 포함(페이지 링크용)
        model.addAttribute("filterQuery", filters.toQueryString());
        model.addAttribute("currentSort", search.getNormalizedSort());
        model.addAttribute("extraQuery",
            filters.toQueryString() + PageQuery.of().add("sort", search.getNormalizedSort()));
        return "customer/product/list";
    }

    @GetMapping("/{productId:\\d+}")
    public String detail(@PathVariable("productId") long productId,
                         @RequestParam(name = "page", defaultValue = "1") int page,
                         Model model) {
        model.addAttribute("product", productService.getProductDetail(productId));
        // 상세 화면의 유일한 페이징이라 파라미터는 공통 pagination 프래그먼트가 쓰는 page를 그대로 쓴다.
        model.addAttribute("reviewPage",
            reviewService.getProductReviews(productId, new PageRequest(page, REVIEW_PAGE_SIZE)));
        return "customer/product/detail";
    }

}
