package com.cakeshop.domain.member.dto.form;

import com.cakeshop.domain.member.dto.view.MemberProfileView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileUpdateForm {

    @NotBlank(message = "이름(닉네임)을 입력해 주세요.")
    @Size(max = 50, message = "이름(닉네임)은 50자 이하여야 합니다.")
    private String nickname;

    @NotBlank(message = "연락처를 입력해 주세요.")
    @Pattern(regexp = "^[0-9+() -]{8,30}$", message = "연락처 형식을 확인해 주세요.")
    private String phone;

    public static ProfileUpdateForm from(MemberProfileView profile) {
        ProfileUpdateForm form = new ProfileUpdateForm();
        form.setNickname(profile.nickname());
        form.setPhone(profile.phone());
        return form;
    }
}
