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

    Optional<Member> findById(@Param("id") Long id);

    int countByEmail(@Param("email") String email);

    int insertMember(Member member);

    int updateProfile(Member member);

    int updatePassword(@Param("id") Long id, @Param("password") String password);

    // 탈퇴는 soft delete — 타 도메인 FK(orders·posts 등)의 이력을 보존한다.
    int withdraw(@Param("id") Long id);

    // 공개 계약(getNicknameMap)용 배치 조회 — id·nickname만 채워진다.
    List<Member> findNicknamesByIds(@Param("ids") Collection<Long> ids);

    // 타 도메인 관리자 목록에 노출할 최소 프로필(id·nickname·email) 배치 조회.
    List<Member> findProfilesByIds(@Param("ids") Collection<Long> ids);

    // 공개 계약(searchMemberIdsByNickname)용 — 닉네임 부분 일치로 id만 반환한다.
    List<Long> findIdsByNicknameLike(@Param("keyword") String keyword);

    List<Long> findIdsByKeyword(@Param("keyword") String keyword);

    // 공개 계약(findAdminMemberIds)용 — 알림 팬아웃 대상인 활성 관리자 id.
    List<Long> findActiveAdminIds();
}
