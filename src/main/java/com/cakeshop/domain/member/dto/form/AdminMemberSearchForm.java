package com.cakeshop.domain.member.dto.form;

import com.cakeshop.domain.member.entity.MemberStatus;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;

/** 관리자 회원 검색 조건. 빈 문자열은 "조건 없음"으로 정규화해 mapper에 넘긴다. */
@Getter
@Setter
public class AdminMemberSearchForm {

    private String name;    // 닉네임 부분 일치
    private String email;   // 이메일 부분 일치
    private String status;  // ACTIVE / SUSPENDED / WITHDRAWN
    private Integer page = 1;

    public String getNormalizedName() {
        return StringUtils.hasText(name) ? name.trim() : null;
    }

    public String getNormalizedEmail() {
        return StringUtils.hasText(email) ? email.trim() : null;
    }

    /** 알 수 없는 값은 조건 없음으로 떨군다 — 화면에서 온 문자열을 그대로 SQL에 넣지 않는다. */
    public String getNormalizedStatus() {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        for (MemberStatus value : MemberStatus.values()) {
            if (value.name().equals(status)) {
                return value.name();
            }
        }
        return null;
    }
}
