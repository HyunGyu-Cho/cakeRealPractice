package com.cakeshop.domain.order.dto.form;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
public class OrderSearchForm {
    private String orderNumber;
    private String memberKeyword;
    private String status;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate orderedFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate orderedTo;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate pickupDate;

    public String normalizedOrderNumber() {
        return blankToNull(orderNumber);
    }

    public String getNormalizedOrderNumber() {
        return normalizedOrderNumber();
    }

    public String normalizedMemberKeyword() {
        return blankToNull(memberKeyword);
    }

    public String getNormalizedMemberKeyword() {
        return normalizedMemberKeyword();
    }

    public String normalizedStatus() {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return com.cakeshop.domain.order.entity.OrderStatus.valueOf(status).name();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public String getNormalizedStatus() {
        return normalizedStatus();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
