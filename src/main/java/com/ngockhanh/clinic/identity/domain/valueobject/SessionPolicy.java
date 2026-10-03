package com.ngockhanh.clinic.identity.domain.valueobject;

import java.time.Duration;

public record SessionPolicy(Duration idleTimeout, Duration absoluteTimeout) {

  public SessionPolicy {
    if (idleTimeout == null
        || absoluteTimeout == null
        || idleTimeout.isNegative()
        || idleTimeout.isZero()
        || absoluteTimeout.isNegative()
        || absoluteTimeout.isZero()
        || absoluteTimeout.compareTo(Duration.ofHours(8)) > 0
        || idleTimeout.compareTo(absoluteTimeout) > 0) {
      throw new IllegalArgumentException("Invalid user session policy");
    }
  }
}
