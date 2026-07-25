package com.cakeshop.domain.cart.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Cart {

    private Long id;
    private Long memberId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
