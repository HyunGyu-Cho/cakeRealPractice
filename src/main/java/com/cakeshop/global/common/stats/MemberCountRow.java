package com.cakeshop.global.common.stats;

/**
 * 회원별 건수 배치 집계의 공통 행 모델.
 *
 * <p>order·review·community가 각자 자기 테이블에서 회원별 건수를 세어 돌려줄 때 쓰는 좁은 운반 타입이다.
 * 도메인마다 같은 모양의 record를 세 벌 만들지 않으려고 {@code StatsPeriod}와 같은 자리에 둔다.
 * 화면 노출용이 아니므로 dto/view가 아니라 global/common/stats에 있다.
 */
public record MemberCountRow(Long memberId, long count) {
}
