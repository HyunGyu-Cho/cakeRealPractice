package com.cakeshop.domain.store.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StoreHolidayForm {

    @NotNull(message = "휴무 날짜를 선택해 주세요.")
    private LocalDate holidayDate;

    @NotBlank(message = "휴무 사유를 입력해 주세요.")
    @Size(max = 100, message = "휴무 사유는 100자 이하여야 합니다.")
    private String reason;
}
