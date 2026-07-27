-- V5: members.status 확정 반영 (ACTIVE / SUSPENDED / WITHDRAWN)
-- 스펙: docs/specs/member.md — 시작 ACTIVE(DEFAULT), 탈퇴는 soft delete(WITHDRAWN + withdrawn_at).
-- 전이(ACTIVE ↔ SUSPENDED, ACTIVE → WITHDRAWN) 검증은 service가 소유하고 CHECK는 값 집합만 지킨다.
-- 로그인 차단: SUSPENDED/WITHDRAWN은 MemberDetailsService가 거부한다.

ALTER TABLE `members`
    DROP CONSTRAINT IF EXISTS `chk_members_status`,
    MODIFY `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD CONSTRAINT `chk_members_status`
        CHECK (`status` IN ('ACTIVE', 'SUSPENDED', 'WITHDRAWN'));
