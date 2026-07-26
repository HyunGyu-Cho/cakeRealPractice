package com.cakeshop.domain.store.controller;

import com.cakeshop.domain.store.dto.form.StoreHolidayForm;
import com.cakeshop.domain.store.dto.form.StoreUpdateForm;
import com.cakeshop.domain.store.dto.view.StoreView;
import com.cakeshop.domain.store.error.StoreErrorCode;
import com.cakeshop.domain.store.service.StoreService;
import com.cakeshop.global.error.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.time.DayOfWeek;
import java.util.List;
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
@RequestMapping("/admin/store")
@RequiredArgsConstructor
public class StoreAdminController {

    private static final List<DayOption> DAY_OPTIONS = List.of(
        new DayOption(DayOfWeek.MONDAY, "월"), 
        new DayOption(DayOfWeek.TUESDAY, "화"),
        new DayOption(DayOfWeek.WEDNESDAY, "수"), 
        new DayOption(DayOfWeek.THURSDAY, "목"),
        new DayOption(DayOfWeek.FRIDAY, "금"), 
        new DayOption(DayOfWeek.SATURDAY, "토"),
        new DayOption(DayOfWeek.SUNDAY, "일")
    );

    private final StoreService storeService;

    @GetMapping
    public String form(Model model) {
        StoreView store = storeService.getStoreView();
        model.addAttribute("storeForm", StoreUpdateForm.from(store));
        model.addAttribute("holidayForm", new StoreHolidayForm());
        addReferenceData(model, store);
        return "admin/store/form";
    }

    @PostMapping
    public String update(@Valid @ModelAttribute("storeForm") StoreUpdateForm form,
                         BindingResult bindingResult,
                         @RequestParam(name = "image", required = false) MultipartFile image,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            // PRG 전 검증 실패는 사용자가 입력한 form을 그대로 재렌더한다.
            model.addAttribute("holidayForm", new StoreHolidayForm());
            addReferenceData(model, storeService.getStoreView());
            return "admin/store/form";
        }

        storeService.updateStore(form, image);
        redirectAttributes.addFlashAttribute("successMessage", "매장 정보를 저장했습니다.");
        return "redirect:/admin/store";
    }

    @PostMapping("/holidays")
    public String addHoliday(@Valid @ModelAttribute("holidayForm") StoreHolidayForm form,
                             BindingResult bindingResult,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                storeService.addHoliday(form);
            } catch (BusinessException e) {
                if (e.getErrorCode() == StoreErrorCode.HOLIDAY_ALREADY_EXISTS
                    || e.getErrorCode() == StoreErrorCode.HOLIDAY_HAS_PICKUP) {
                    // 화면에서 바로 고칠 수 있는 업무 오류는 해당 입력 필드에 돌려준다.
                    bindingResult.rejectValue("holidayDate", e.getErrorCode().code(), e.getMessage());
                } else {
                    throw e;
                }
            }
        }

        if (bindingResult.hasErrors()) {
            StoreView store = storeService.getStoreView();
            model.addAttribute("storeForm", StoreUpdateForm.from(store));
            addReferenceData(model, store);
            return "admin/store/form";
        }

        redirectAttributes.addFlashAttribute("successMessage", "특정 휴무일을 추가했습니다.");
        return "redirect:/admin/store";
    }

    @PostMapping("/holidays/{holidayId}/delete")
    public String deleteHoliday(@PathVariable long holidayId, RedirectAttributes redirectAttributes) {
        storeService.deleteHoliday(holidayId);
        redirectAttributes.addFlashAttribute("successMessage", "특정 휴무일을 삭제했습니다.");
        return "redirect:/admin/store";
    }

    private void addReferenceData(Model model, StoreView store) {
        model.addAttribute("dayOptions", DAY_OPTIONS);
        model.addAttribute("holidays", store.holidays());
        // 이미지는 form 객체가 아닌 조회 결과에서만 노출한다(업로드는 MultipartFile 로 별도 처리).
        model.addAttribute("currentImageUrl", store.imageUrl());
    }

    public record DayOption(DayOfWeek value, String label) {
    }
}
