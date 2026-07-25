package com.cakeshop.domain.member.entity;

// 회원 상태 3개 — 값 정본은 docs/specs/member.md. 전이(ACTIVE↔SUSPENDED, ACTIVE→WITHDRAWN)는 service가 소유한다.
public enum MemberStatus {

    ACTIVE,     // 정상 (가입 시작 상태, DB DEFAULT)
    SUSPENDED,  // 이용 제한 (관리자 정지 — 로그인 거부)
    WITHDRAWN;  // 탈퇴 (soft delete, 최종 — 로그인 거부)

    public boolean matches(String status) {
        return name().equals(status);
    }
}
