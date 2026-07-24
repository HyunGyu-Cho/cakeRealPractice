package com.cakeshop.domain.member.mapper;

import com.cakeshop.domain.member.entity.Member;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemberMapper {

    Optional<Member> findByEmail(@Param("email") String email);

    // 공개 계약(getNicknameMap)용 배치 조회 — id·nickname만 채워진다.
    List<Member> findNicknamesByIds(@Param("ids") Collection<Long> ids);

    // 공개 계약(searchMemberIdsByNickname)용 — 닉네임 부분 일치로 id만 반환한다.
    List<Long> findIdsByNicknameLike(@Param("keyword") String keyword);
}
