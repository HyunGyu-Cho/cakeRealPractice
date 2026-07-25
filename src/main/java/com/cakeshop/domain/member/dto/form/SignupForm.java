package com.cakeshop.domain.member.dto.form;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignupForm {

    @NotBlank(message = "이메일을 입력해 주세요.")
    @Email(message = "이메일 형식을 확인해 주세요.")
    @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
    private String email;

    // BCrypt는 72바이트까지만 반영하므로 상한을 함께 둔다.
    @NotBlank(message = "비밀번호를 입력해 주세요.")
    @Size(min = 8, max = 72, message = "비밀번호는 8자 이상 72자 이하여야 합니다.")
    private String password;

    @NotBlank(message = "비밀번호 확인을 입력해 주세요.")
    private String passwordConfirm;

    @NotBlank(message = "이름(닉네임)을 입력해 주세요.")
    @Size(max = 50, message = "이름(닉네임)은 50자 이하여야 합니다.")
    private String nickname;

    @NotBlank(message = "연락처를 입력해 주세요.")
    @Pattern(regexp = "^[0-9+() -]{8,30}$", message = "연락처 형식을 확인해 주세요.")
    private String phone;

    @AssertTrue(message = "서비스 이용약관에 동의해 주세요.")
    private boolean termsService;

    @AssertTrue(message = "개인정보 수집·이용에 동의해 주세요.")
    private boolean termsPrivacy;

    // 선택 동의 — 검증 없음. 동의 이력 저장은 1차 범위가 아니다(스펙 참조).
    private boolean marketing;

    @AssertTrue(message = "비밀번호가 일치하지 않습니다.")
    public boolean isPasswordConfirmed() {
        return password == null || passwordConfirm == null || password.equals(passwordConfirm);
    }
}
