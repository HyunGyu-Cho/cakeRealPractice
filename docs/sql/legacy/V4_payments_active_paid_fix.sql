-- V4: payments.active_paid_order_id 생성 열 조건 수정 (주환 검토 필요)
-- 문제: 생성 열이 status = 'PAID' 기준인데 확정 PaymentStatus(READY/DONE/CANCELED/
--       PARTIAL_CANCELED/ABORTED/EXPIRED)에는 PAID가 없다(결제 승인 완료 = DONE).
--       조건이 영원히 거짓이라 열이 항상 NULL이고, uk_payments_active_paid_order 의
--       "주문당 활성 결제 1건" 보장이 무력화된 상태였다.
-- 수정: 기준을 DONE으로 교체. STORED 생성 열은 표현식 변경(MODIFY)이 불가하므로
--       제약·컬럼을 제거 후 재생성한다. 재생성 시 기존 행의 값도 새 조건으로 재계산된다.

ALTER TABLE `payments`
    DROP CONSTRAINT IF EXISTS `uk_payments_active_paid_order`;

ALTER TABLE `payments`
    DROP COLUMN IF EXISTS `active_paid_order_id`;

ALTER TABLE `payments`
    ADD COLUMN `active_paid_order_id` BIGINT
        GENERATED ALWAYS AS (
            CASE WHEN `status` = 'DONE' THEN `order_id` ELSE NULL END
        ) STORED
        AFTER `provider_status`,
    ADD CONSTRAINT `uk_payments_active_paid_order` UNIQUE (`active_paid_order_id`);
