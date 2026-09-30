package com.ngockhanh.clinic.identity.infrastructure.session;

import java.time.Duration;

public record LoginThrottleSettings(int usernameLimit, int ipLimit, Duration window) {
    public LoginThrottleSettings {
        if (usernameLimit < 1 || ipLimit < 1 || window == null || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("Invalid login throttle configuration");
        }
    }
}
