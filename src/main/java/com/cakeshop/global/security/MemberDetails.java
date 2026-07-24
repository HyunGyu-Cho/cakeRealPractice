package com.cakeshop.global.security;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class MemberDetails extends User {

    private final Long memberId;

    public MemberDetails(Long memberId, String email, String password,
                         Collection<? extends GrantedAuthority> authorities) {
        super(email, password, authorities);
        this.memberId = memberId;
    }

    public Long getMemberId() {
        return memberId;
    }
}
