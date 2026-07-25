package com.cakeshop.domain.payment.dto.form;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
public class PaymentSearchForm {
    private String status;
    private String method;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate paidFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate paidTo;

    public String normalizedStatus() {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return com.cakeshop.domain.payment.entity.PaymentStatus.valueOf(status).name();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public String getNormalizedStatus() {
        return normalizedStatus();
    }

    public String normalizedMethod() {
        return method == null || method.isBlank() ? null : method.trim();
    }

    public String getNormalizedMethod() {
        return normalizedMethod();
    }
}
