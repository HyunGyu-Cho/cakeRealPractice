-- V3: orders.status 팀 확정값 반영 (OrderStatus 7개)
--   일반 케이크: PAID → READY_FOR_PICKUP → PICKED_UP
--   수제 케이크: UNDER_REVIEW → IN_PRODUCTION → READY_FOR_PICKUP → PICKED_UP
--   예외: UNDER_REVIEW → REJECTED(반려) / 최종 전 모든 상태 → CANCELED
-- DB 컬럼 규칙(전원 합의)에 맞춰 VARCHAR(20) NOT NULL + chk_orders_status CHECK 적용.
-- 시작 상태가 주문 유형별로 다르므로(일반 PAID / 수제 UNDER_REVIEW) DEFAULT는 두지 않고
-- 주문 생성 시 서비스가 명시적으로 세팅한다.
-- 전이 규칙은 OrderStatus.canTransitionTo()가 소유하며, CHECK는 허용 값 집합만 지킨다.

ALTER TABLE `orders`
    DROP CONSTRAINT IF EXISTS `chk_orders_status`,
    MODIFY `status` VARCHAR(20) NOT NULL,
    ADD CONSTRAINT `chk_orders_status`
        CHECK (`status` IN ('UNDER_REVIEW', 'IN_PRODUCTION', 'REJECTED',
                            'PAID', 'READY_FOR_PICKUP', 'PICKED_UP', 'CANCELED'));

-- 삭제된 옛 상태(APPROVED / PENDING_PAYMENT / ACCEPTED)에 묶인 컬럼 정리.
-- rejected_at·ready_at·picked_up_at·canceled_* 는 새 모델에도 대응 상태가 있어 유지한다.
ALTER TABLE `orders`
    DROP COLUMN `cancellation_blocked_at`,
    DROP COLUMN `payment_expires_at`,
    DROP COLUMN `approved_at`,
    DROP COLUMN `accepted_at`;
