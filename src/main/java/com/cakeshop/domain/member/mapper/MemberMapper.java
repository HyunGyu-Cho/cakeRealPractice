package com.cakeshop.domain.member.mapper;

import com.cakeshop.domain.member.dto.form.AdminMemberSearchForm;
import com.cakeshop.domain.member.entity.Member;
import java.time.LocalDateTime;
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

    // 공개 계약(countNewMembers)용 — statistics 신규 가입 집계.
    long countCreatedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // ==================== 관리자 회원 관리 ====================

    long countAdminMembers(@Param("cond") AdminMemberSearchForm cond);

    List<Member> findAdminMemberPage(@Param("cond") AdminMemberSearchForm cond,
                                     @Param("size") int size,
                                     @Param("offset") int offset);

    // 상태 전이는 service가 검증한 뒤 현재 상태를 건 조건부 UPDATE로만 반영한다(동시 요청 방어).
    // suspended_at은 업무 시각이라 SQL에서 세팅한다(withdraw와 같은 예외).
    int suspend(@Param("id") Long id, @Param("reason") String reason);

    int unsuspend(@Param("id") Long id);
}
