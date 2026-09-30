package com.ngockhanh.clinic.identity.domain.valueobject;

import java.time.Duration;

public record StaffSessionPolicy(Duration idleTimeout, Duration absoluteTimeout) {

    public StaffSessionPolicy {
        if (idleTimeout == null || absoluteTimeout == null || idleTimeout.isNegative() || idleTimeout.isZero()
                || absoluteTimeout.isNegative() || absoluteTimeout.isZero()
                || absoluteTimeout.compareTo(Duration.ofHours(8)) > 0
                || idleTimeout.compareTo(absoluteTimeout) > 0) {
            throw new IllegalArgumentException("Invalid staff session policy");
        }
    }
}
