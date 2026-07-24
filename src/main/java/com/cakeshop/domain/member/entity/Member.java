package com.cakeshop.domain.member.entity;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** 인증 조회에도 사용하는 회원 영속 모델이다. 비밀번호는 BCrypt 해시만 저장한다. */
@Getter
@Setter
public class Member {

    private Long id;
    private String email;
    private String password;
    private String nickname;
    private String phone;
    private String role;
    private String status;
    private LocalDateTime suspendedAt;
    private String suspendedReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime withdrawnAt;

}
