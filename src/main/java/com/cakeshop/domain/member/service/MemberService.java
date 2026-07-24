package com.cakeshop.domain.member.service;

import com.cakeshop.domain.member.entity.Member;
import com.cakeshop.domain.member.mapper.MemberMapper;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

    private final MemberMapper memberMapper;

    public MemberService(MemberMapper memberMapper) {
        this.memberMapper = memberMapper;
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
