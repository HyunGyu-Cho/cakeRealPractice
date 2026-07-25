package com.cakeshop.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.member.mapper.MemberMapper;
import com.cakeshop.domain.member.entity.Member;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class MemberDetailsServiceTests {

    private static final String LOCAL_ADMIN_HASH =
        "$2a$10$wRIE78x8sm..uLtbp9LHde7l6wUWQD3NjPvThQaXvZ3PpXfW6wwX.";

    @Mock
    private MemberMapper memberMapper;

    private MemberDetailsService memberDetailsService;

    @BeforeEach
    void setUp() {
        memberDetailsService = new MemberDetailsService(memberMapper);
    }

    @Test
    void loadsAdminAuthorityByEmail() {
        Member admin = new Member();
        admin.setId(1L);
        admin.setEmail("admin@cakeshop.local");
        admin.setPassword(LOCAL_ADMIN_HASH);
        admin.setRole("ADMIN");
        when(memberMapper.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));

        MemberDetails details = (MemberDetails) memberDetailsService.loadUserByUsername(admin.getEmail());

        assertThat(details.getMemberId()).isEqualTo(1L);
        assertThat(details.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
    }

    @Test
    void localAdminSqlHashMatchesDocumentedPassword() {
        assertThat(new BCryptPasswordEncoder().matches("Admin1234!", LOCAL_ADMIN_HASH)).isTrue();
    }

    @Test
    void unknownEmailFailsAuthentication() {
        when(memberMapper.findByEmail("missing@cakeshop.local")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberDetailsService.loadUserByUsername("missing@cakeshop.local"))
            .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void suspendedMemberCannotLogin() {
        Member suspended = memberWithStatus("SUSPENDED");
        when(memberMapper.findByEmail(suspended.getEmail())).thenReturn(Optional.of(suspended));

        assertThatThrownBy(() -> memberDetailsService.loadUserByUsername(suspended.getEmail()))
            .isInstanceOf(LockedException.class);
    }

    @Test
    void withdrawnMemberFailsLikeUnknownEmail() {
        Member withdrawn = memberWithStatus("WITHDRAWN");
        when(memberMapper.findByEmail(withdrawn.getEmail())).thenReturn(Optional.of(withdrawn));

        // 탈퇴 여부가 로그인 화면에 노출되지 않도록 미존재 계정과 같은 예외로 처리한다.
        assertThatThrownBy(() -> memberDetailsService.loadUserByUsername(withdrawn.getEmail()))
            .isInstanceOf(UsernameNotFoundException.class);
    }

    private Member memberWithStatus(String status) {
        Member member = new Member();
        member.setId(2L);
        member.setEmail("user@cakeshop.local");
        member.setPassword(LOCAL_ADMIN_HASH);
        member.setRole("USER");
        member.setStatus(status);
        return member;
    }
}
