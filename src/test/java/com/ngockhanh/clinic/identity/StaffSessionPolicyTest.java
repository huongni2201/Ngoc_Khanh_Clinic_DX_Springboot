package com.ngockhanh.clinic.identity;

import com.ngockhanh.clinic.identity.domain.valueobject.SessionPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionPolicyTest {
    @Test
    void acceptsIdleAndAbsoluteTimeoutsWithinPolicy() {
        assertThat(new SessionPolicy(Duration.ofMinutes(30), Duration.ofHours(8)))
                .isEqualTo(new SessionPolicy(Duration.ofMinutes(30), Duration.ofHours(8)));
    }

    @Test
    void rejectsMissingNonPositiveAndOutOfRangeTimeouts() {
        assertThatThrownBy(() -> new SessionPolicy(null, Duration.ofHours(8)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SessionPolicy(Duration.ZERO, Duration.ofHours(8)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SessionPolicy(Duration.ofMinutes(30), Duration.ofHours(9)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SessionPolicy(Duration.ofMinutes(31), Duration.ofMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
