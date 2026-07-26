package com.cakeshop.domain.member.entity;

// 회원 상태 3개 — 값 정본은 docs/specs/member.md. 전이(ACTIVE↔SUSPENDED, ACTIVE→WITHDRAWN)는 service가 소유한다.
public enum MemberStatus {

    ACTIVE("정상"),         // 가입 시작 상태, DB DEFAULT
    SUSPENDED("이용 제한"), // 관리자 정지 — 로그인 거부
    WITHDRAWN("탈퇴");      // soft delete, 최종 — 로그인 거부

    private final String label;

    MemberStatus(String label) {
        this.label = label;
    }

    /** 한글 라벨은 저장하지 않고 화면에서만 쓴다. */
    public String label() {
        return label;
    }

    public boolean matches(String status) {
        return name().equals(status);
    }

    /**
     * 관리자가 낼 수 있는 전이인지. 본인 탈퇴(ACTIVE → WITHDRAWN)는 여기 포함하지 않는다 —
     * 관리자에 의한 강제 탈퇴는 범위 밖이다(docs/specs/member.md 8.1).
     */
    public boolean canAdminTransitionTo(MemberStatus next) {
        return (this == ACTIVE && next == SUSPENDED) || (this == SUSPENDED && next == ACTIVE);
    }
}
