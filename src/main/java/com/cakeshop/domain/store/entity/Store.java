package com.cakeshop.domain.store.entity;

import java.time.LocalDateTime;
import java.time.LocalTime;

import lombok.Getter;
import lombok.Setter;

/** DB의 store 한 행을 표현한다. 화면 입력값은 dto/form에서 별도로 받는다. */
@Getter
@Setter
public class Store {

    private Long id;
    private String name;
    private String description;
    private String imageUrl;
    private String address;
    private String phone;
    private String pickupPlace;
    private LocalTime pickupStartTime;
    private LocalTime pickupEndTime;
    private Integer pickupIntervalMinutes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
