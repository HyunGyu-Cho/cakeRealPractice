package com.cakeshop.global.security;

import com.cakeshop.domain.member.mapper.MemberMapper;
import com.cakeshop.domain.member.entity.Member;
import com.cakeshop.domain.member.entity.MemberStatus;
import java.util.List;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class MemberDetailsService implements UserDetailsService {

    private final MemberMapper memberMapper;

    public MemberDetailsService(MemberMapper memberMapper) {
        this.memberMapper = memberMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // 복붙·모바일 자동완성으로 이메일 앞뒤에 섞이는 공백은 조회 전에 제거한다.
        String normalizedEmail = email == null ? "" : email.trim();
        Member member = memberMapper.findByEmail(normalizedEmail)
            // 계정 존재 여부를 로그인 화면에 노출하지 않도록 동일한 인증 실패로 처리한다.
            .orElseThrow(() -> new UsernameNotFoundException("이메일 또는 비밀번호가 올바르지 않습니다."));

        // 상태별 로그인 차단(스펙 docs/specs/member.md). 탈퇴는 계정 존재 여부를 숨기려 미존재와 동일하게 처리한다.
        if (MemberStatus.WITHDRAWN.matches(member.getStatus())) {
            throw new UsernameNotFoundException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        if (MemberStatus.SUSPENDED.matches(member.getStatus())) {
            throw new LockedException("이용이 제한된 계정입니다.");
        }

        return new MemberDetails(
            member.getId(), member.getEmail(), member.getPassword(),
            // DB에는 접두어 없는 역할(USER/ADMIN)을 저장하고, 권한 문자열로 변환할 때 ROLE_ 접두어를 붙인다.
            List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole()))
        );
    }
}
