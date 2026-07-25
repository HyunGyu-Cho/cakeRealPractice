package com.cakeshop.domain.order.dto.form;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
public class PickupForm {
    @NotNull(message = "픽업 일시를 선택해 주세요.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime pickupAt;
}
