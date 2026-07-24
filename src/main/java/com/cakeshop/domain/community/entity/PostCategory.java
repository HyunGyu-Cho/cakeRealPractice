package com.cakeshop.domain.community.entity;

import lombok.Getter;
import lombok.Setter;

/** 글 종류 lookup — code가 저장·필터 기준값이고 name은 화면 라벨이다(status 아님). */
@Getter
@Setter
public class PostCategory {

    private Long id;
    private String code;
    private String name;
    private boolean active;
    private int sortOrder;

}
