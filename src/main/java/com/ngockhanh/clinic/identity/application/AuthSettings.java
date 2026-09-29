package com.ngockhanh.clinic.identity.application;

import java.time.Duration;

public record AuthSettings(Duration idleTimeout, Duration absoluteTimeout) {
  public AuthSettings {
    if (idleTimeout == null || absoluteTimeout == null || idleTimeout.isNegative() || idleTimeout.isZero()
        || absoluteTimeout.isNegative() || absoluteTimeout.isZero()
        || absoluteTimeout.compareTo(Duration.ofHours(8)) > 0 || idleTimeout.compareTo(absoluteTimeout) > 0) {
      throw new IllegalArgumentException("Invalid staff session configuration");
    }
  }
}
