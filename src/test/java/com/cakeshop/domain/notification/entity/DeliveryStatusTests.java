package com.cakeshop.domain.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DeliveryStatusTests {

    @Test
    void requestedGoesOnlyToSentOrFailed() {
        assertAllowed(DeliveryStatus.REQUESTED,
            EnumSet.of(DeliveryStatus.SENT, DeliveryStatus.FAILED));
    }

    /** 재시도가 또 실패하면 상태는 FAILED 그대로 두고 retry_count만 올린다(자기 전이). */
    @Test
    void failedGoesToSentFailedOrAbandoned() {
        assertAllowed(DeliveryStatus.FAILED,
            EnumSet.of(DeliveryStatus.SENT, DeliveryStatus.FAILED, DeliveryStatus.ABANDONED));
    }

    @Test
    void finalStatesAcceptNoTransition() {
        assertThat(DeliveryStatus.SENT.isFinal()).isTrue();
        assertThat(DeliveryStatus.ABANDONED.isFinal()).isTrue();
        assertAllowed(DeliveryStatus.SENT, EnumSet.noneOf(DeliveryStatus.class));
        assertAllowed(DeliveryStatus.ABANDONED, EnumSet.noneOf(DeliveryStatus.class));
    }

    @Test
    void nonFinalStatesAreRequestedAndFailed() {
        assertThat(DeliveryStatus.REQUESTED.isFinal()).isFalse();
        assertThat(DeliveryStatus.FAILED.isFinal()).isFalse();
    }

    private void assertAllowed(DeliveryStatus from, Set<DeliveryStatus> allowed) {
        for (DeliveryStatus next : DeliveryStatus.values()) {
            assertThat(from.canTransitionTo(next))
                .as("%s -> %s", from, next)
                .isEqualTo(allowed.contains(next));
        }
    }
}
