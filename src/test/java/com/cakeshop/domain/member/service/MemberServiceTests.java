package com.cakeshop.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.member.dto.form.PasswordForm;
import com.cakeshop.domain.member.dto.form.SignupForm;
import com.cakeshop.domain.member.entity.Member;
import com.cakeshop.domain.member.error.MemberErrorCode;
import com.cakeshop.domain.member.mapper.MemberMapper;
import com.cakeshop.global.error.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class MemberServiceTests {

    @Mock
    private MemberMapper memberMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(memberMapper, passwordEncoder);
    }

    @Test
    void signupEncodesPasswordAndInsertsMember() {
        when(memberMapper.countByEmail("new@cakeshop.local")).thenReturn(0);
        when(passwordEncoder.encode("Password1!")).thenReturn("ENCODED");

        memberService.signup(signupForm());

        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberMapper).insertMember(captor.capture());
        Member saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("new@cakeshop.local");
        assertThat(saved.getPassword()).isEqualTo("ENCODED");
        assertThat(saved.getNickname()).isEqualTo("신규회원");
        // role·status는 세팅하지 않는다 — DDL DEFAULT(USER/ACTIVE)에 위임
        assertThat(saved.getRole()).isNull();
        assertThat(saved.getStatus()).isNull();
    }

    @Test
    void signupRejectsDuplicateEmail() {
        when(memberMapper.countByEmail("new@cakeshop.local")).thenReturn(1);

        assertThatThrownBy(() -> memberService.signup(signupForm()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(MemberErrorCode.DUPLICATE_EMAIL);
        verify(memberMapper, never()).insertMember(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() {
        when(memberMapper.findById(1L)).thenReturn(Optional.of(member()));
        when(passwordEncoder.matches("wrong", "STORED_HASH")).thenReturn(false);

        assertThatThrownBy(() -> memberService.changePassword(1L, passwordForm("wrong")))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(MemberErrorCode.PASSWORD_MISMATCH);
        verify(memberMapper, never()).updatePassword(org.mockito.ArgumentMatchers.anyLong(), anyString());
    }

    @Test
    void changePasswordEncodesNewPassword() {
        when(memberMapper.findById(1L)).thenReturn(Optional.of(member()));
        when(passwordEncoder.matches("Current1!", "STORED_HASH")).thenReturn(true);
        when(passwordEncoder.encode("NewPassword1!")).thenReturn("NEW_ENCODED");

        memberService.changePassword(1L, passwordForm("Current1!"));

        verify(memberMapper).updatePassword(1L, "NEW_ENCODED");
    }

    @Test
    void withdrawMarksMemberWithdrawn() {
        when(memberMapper.findById(1L)).thenReturn(Optional.of(member()));

        memberService.withdraw(1L);

        verify(memberMapper).withdraw(1L);
    }

    @Test
    void getProfileFailsWhenMemberMissing() {
        when(memberMapper.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.getProfile(99L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(MemberErrorCode.NOT_FOUND);
    }

    private SignupForm signupForm() {
        SignupForm form = new SignupForm();
        form.setEmail("new@cakeshop.local");
        form.setPassword("Password1!");
        form.setPasswordConfirm("Password1!");
        form.setNickname("신규회원");
        form.setPhone("010-1234-5678");
        form.setTermsService(true);
        form.setTermsPrivacy(true);
        return form;
    }

    private PasswordForm passwordForm(String currentPassword) {
        PasswordForm form = new PasswordForm();
        form.setCurrentPassword(currentPassword);
        form.setNewPassword("NewPassword1!");
        form.setNewPasswordConfirm("NewPassword1!");
        return form;
    }

    private Member member() {
        Member member = new Member();
        member.setId(1L);
        member.setEmail("user@cakeshop.local");
        member.setPassword("STORED_HASH");
        member.setNickname("테스트회원");
        member.setPhone("010-0000-0002");
        member.setStatus("ACTIVE");
        return member;
    }
}
