package com.cakeshop.domain.product.controller;

import com.cakeshop.domain.product.dto.form.AdminProductSearchForm;
import com.cakeshop.domain.product.dto.form.ProductForm;
import com.cakeshop.domain.product.entity.ProductStatus;
import com.cakeshop.domain.product.entity.ProductType;
import com.cakeshop.domain.product.service.ProductAdminService;
import com.cakeshop.global.common.paging.PageQuery;
import com.cakeshop.global.common.paging.PageRequest;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/products")
public class ProductAdminController {

    private static final int PAGE_SIZE = 10;

    private final ProductAdminService productAdminService;

    public ProductAdminController(ProductAdminService productAdminService) {
        this.productAdminService = productAdminService;
    }

    @GetMapping
    public String list(@ModelAttribute("search") AdminProductSearchForm search, Model model) {
        model.addAttribute("pageResult",
            productAdminService.getAdminProductPage(search, new PageRequest(search.getPage(), PAGE_SIZE)));
        model.addAttribute("extraQuery", PageQuery.of()
            .add("keyword", search.getNormalizedKeyword())
            .add("type", search.getNormalizedType())
            .add("status", search.getNormalizedStatus())
            .add("stock", search.getNormalizedStock())
            .toQueryString());
        return "admin/product/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("productForm", new ProductForm());
        addReferenceData(model, null, null);
        return "admin/product/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("productForm") ProductForm form,
                         BindingResult bindingResult,
                         @RequestParam(name = "image", required = false) MultipartFile image,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addReferenceData(model, null, null);
            return "admin/product/form";
        }
        productAdminService.createProduct(form, image);
        redirectAttributes.addFlashAttribute("successMessage", "상품을 등록했습니다.");
        return "redirect:/admin/products";
    }

    @GetMapping("/{productId}/edit")
    public String editForm(@PathVariable long productId, Model model) {
        model.addAttribute("productForm", productAdminService.getProductForm(productId));
        addReferenceData(model, productId, productAdminService.getMainImageUrl(productId));
        return "admin/product/form";
    }

    @PostMapping("/{productId}/edit")
    public String update(@PathVariable long productId,
                         @Valid @ModelAttribute("productForm") ProductForm form,
                         BindingResult bindingResult,
                         @RequestParam(name = "image", required = false) MultipartFile image,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addReferenceData(model, productId, productAdminService.getMainImageUrl(productId));
            return "admin/product/form";
        }
        productAdminService.updateProduct(productId, form, image);
        redirectAttributes.addFlashAttribute("successMessage", "상품 정보를 저장했습니다.");
        return "redirect:/admin/products";
    }

    @PostMapping("/{productId}/status")
    public String toggleStatus(@PathVariable long productId, RedirectAttributes redirectAttributes) {
        ProductStatus next = productAdminService.toggleStatus(productId);
        redirectAttributes.addFlashAttribute("successMessage",
            next == ProductStatus.ACTIVE ? "판매를 재개했습니다." : "판매를 중지했습니다.");
        return "redirect:/admin/products";
    }

    private void addReferenceData(Model model, Long productId, String currentImageUrl) {
        model.addAttribute("productTypes", ProductType.values());
        model.addAttribute("productStatuses", ProductStatus.values());
        model.addAttribute("editingProductId", productId);
        // 이미지는 form 객체가 아닌 조회 결과로만 노출한다(업로드는 MultipartFile 별도 처리 — store 패턴).
        model.addAttribute("currentImageUrl", currentImageUrl);
    }

}
