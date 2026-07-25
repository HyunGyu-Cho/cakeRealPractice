package com.cakeshop.domain.member.service;

import com.cakeshop.domain.member.dto.form.PasswordForm;
import com.cakeshop.domain.member.dto.form.ProfileUpdateForm;
import com.cakeshop.domain.member.dto.form.SignupForm;
import com.cakeshop.domain.member.dto.view.MemberProfileView;
import com.cakeshop.domain.member.entity.Member;
import com.cakeshop.domain.member.error.MemberErrorCode;
import com.cakeshop.domain.member.mapper.MemberMapper;
import com.cakeshop.global.error.BusinessException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;

    public MemberService(MemberMapper memberMapper, PasswordEncoder passwordEncoder) {
        this.memberMapper = memberMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /** 회원가입. role·status·created_at은 DDL DEFAULT(USER/ACTIVE)에 위임한다. */
    @Transactional
    public void signup(SignupForm form) {
        String email = form.getEmail().trim();
        if (memberMapper.countByEmail(email) > 0) {
            throw new BusinessException(MemberErrorCode.DUPLICATE_EMAIL);
        }
        Member member = new Member();
        member.setEmail(email);
        member.setPassword(passwordEncoder.encode(form.getPassword()));
        member.setNickname(form.getNickname().trim());
        member.setPhone(form.getPhone().trim());
        memberMapper.insertMember(member);
    }

    @Transactional(readOnly = true)
    public MemberProfileView getProfile(Long memberId) {
        Member member = findMember(memberId);
        return new MemberProfileView(
            member.getId(), member.getNickname(), member.getEmail(),
            member.getPhone(), member.getCreatedAt()
        );
    }

    @Transactional
    public void updateProfile(Long memberId, ProfileUpdateForm form) {
        Member member = findMember(memberId);
        member.setNickname(form.getNickname().trim());
        member.setPhone(form.getPhone().trim());
        memberMapper.updateProfile(member);
    }

    @Transactional
    public void changePassword(Long memberId, PasswordForm form) {
        Member member = findMember(memberId);
        if (!passwordEncoder.matches(form.getCurrentPassword(), member.getPassword())) {
            throw new BusinessException(MemberErrorCode.PASSWORD_MISMATCH);
        }
        memberMapper.updatePassword(memberId, passwordEncoder.encode(form.getNewPassword()));
    }

    /** 탈퇴는 soft delete(status=WITHDRAWN + withdrawn_at) — FK로 참조하는 타 도메인 이력을 보존한다. */
    @Transactional
    public void withdraw(Long memberId) {
        findMember(memberId);
        memberMapper.withdraw(memberId);
    }

    private Member findMember(Long memberId) {
        return memberMapper.findById(memberId)
            .orElseThrow(() -> new BusinessException(MemberErrorCode.NOT_FOUND));
    }

    /**
     * [공개 계약] 타 도메인이 작성자 닉네임을 표시할 때 쓰는 배치 조회.
     * community 목록이 첫 사용처다 — 시그니처 변경 시 사용처(수민↔현규) 합의 필요.
     */
    @Transactional(readOnly = true)
    public Map<Long, String> getNicknameMap(Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        return memberMapper.findNicknamesByIds(memberIds).stream()
            .collect(Collectors.toMap(Member::getId, Member::getNickname));
    }

    /**
     * [공개 계약] 닉네임 부분 일치로 회원 id를 찾는다.
     * community 관리자 작성자 검색이 첫 사용처다 — 시그니처 변경 시 사용처(수민↔현규) 합의 필요.
     */
    @Transactional(readOnly = true)
    public List<Long> searchMemberIdsByNickname(String keyword) {
        return memberMapper.findIdsByNicknameLike(keyword);
    }
}
