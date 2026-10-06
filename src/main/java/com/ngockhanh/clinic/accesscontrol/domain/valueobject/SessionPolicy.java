package com.ngockhanh.clinic.accesscontrol.domain.valueobject;

import java.time.Duration;

/**
 * Session lifetime: a session ends after {@code idleTimeout} without requests or {@code
 * absoluteTimeout} after sign-in, whichever comes first (ADR-0014).
 */
public record SessionPolicy(Duration idleTimeout, Duration absoluteTimeout) {
  private static final Duration MAX_ABSOLUTE_TIMEOUT = Duration.ofHours(12);

  public SessionPolicy {
    if (idleTimeout == null || absoluteTimeout == null)
      throw new IllegalArgumentException("Session timeouts are required");
    if (!idleTimeout.isPositive() || idleTimeout.compareTo(absoluteTimeout) > 0)
      throw new IllegalArgumentException("Idle timeout must be positive and not exceed absolute");
    if (absoluteTimeout.compareTo(MAX_ABSOLUTE_TIMEOUT) > 0)
      throw new IllegalArgumentException("Absolute session timeout must not exceed 12 hours");
  }

  /**
   * Time-to-live to apply now: the idle timeout, capped by the time left before absolute expiry.
   */
  public Duration ttl(Duration remainingUntilAbsoluteExpiry) {
    return idleTimeout.compareTo(remainingUntilAbsoluteExpiry) < 0
        ? idleTimeout
        : remainingUntilAbsoluteExpiry;
  }
}
