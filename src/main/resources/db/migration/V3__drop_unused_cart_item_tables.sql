-- V3: 설계에서 빠진 cart_item_images·cart_item_options 정리
--
-- [배경]
--   두 테이블은 장바구니 구현(2026-07-25, 커밋 1e18e01) 때 설계에서 제외됐다. 구 V0_ERD.sql에서는
--   지워졌지만 이미 만들어진 DB에는 DROP이 나가지 않아 빈 채로 남아 있다.
--   실제로 참조하는 매퍼·엔티티가 하나도 없고 행도 0건이다.
--
-- [왜 지금]
--   베이스라인(V1)에는 없는 테이블이라, 새로 만든 DB와 기존 DB의 스키마가 이 두 개만큼 어긋난다.
--   Flyway 전환의 목적이 "모든 환경이 같은 스키마"이므로 여기서 맞춰 둔다.
--
-- 새 DB에는 애초에 없으므로 IF EXISTS로 조용히 넘어간다.

DROP TABLE IF EXISTS `cart_item_images`;
DROP TABLE IF EXISTS `cart_item_options`;
