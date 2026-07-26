package com.cakeshop.domain.order.dto.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/** 관리자 견적 발송 입력. 재견적도 같은 폼을 쓴다(새 회차로 저장). */
@Getter
@Setter
public class QuoteForm {

    @NotNull(message = "견적 금액을 입력해 주세요.")
    @Positive(message = "견적 금액은 0원보다 커야 합니다.")
    private Long quotedAmount;

    @NotNull(message = "제작 가능일을 입력해 주세요.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate producibleDate;

    @Size(max = 500, message = "안내 문구는 500자 이내로 입력해 주세요.")
    private String adminNote;
}
