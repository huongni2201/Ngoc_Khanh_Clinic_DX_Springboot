package com.ngockhanh.clinic.identity;

import com.ngockhanh.clinic.identity.domain.valueobject.StaffSessionPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StaffSessionPolicyTest {
    @Test
    void acceptsIdleAndAbsoluteTimeoutsWithinPolicy() {
        assertThat(new StaffSessionPolicy(Duration.ofMinutes(30), Duration.ofHours(8)))
                .isEqualTo(new StaffSessionPolicy(Duration.ofMinutes(30), Duration.ofHours(8)));
    }

    @Test
    void rejectsMissingNonPositiveAndOutOfRangeTimeouts() {
        assertThatThrownBy(() -> new StaffSessionPolicy(null, Duration.ofHours(8)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StaffSessionPolicy(Duration.ZERO, Duration.ofHours(8)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StaffSessionPolicy(Duration.ofMinutes(30), Duration.ofHours(9)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StaffSessionPolicy(Duration.ofMinutes(31), Duration.ofMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
