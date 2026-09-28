package com.ngockhanh.clinic.identity.application.port;

import java.time.Duration;
import java.util.List;

public record AuthSettings(Duration idleTimeout, Duration absoluteTimeout, String issuer, String audience,
                           boolean secureCookie, boolean businessAccess, List<String> origins,
                           int usernameLimit, int ipLimit, Duration throttleWindow, List<String> trustedProxies) {
  public AuthSettings {
    origins = List.copyOf(origins);
    trustedProxies = List.copyOf(trustedProxies);
    if (idleTimeout.isNegative() || idleTimeout.isZero() || absoluteTimeout.isNegative() || absoluteTimeout.isZero()
        || absoluteTimeout.compareTo(Duration.ofHours(8)) > 0 || idleTimeout.compareTo(absoluteTimeout) > 0
        || usernameLimit < 1 || ipLimit < 1 || throttleWindow.isNegative() || throttleWindow.isZero()
        || issuer.isBlank() || audience.isBlank() || origins.contains("*")) {
      throw new IllegalArgumentException("Invalid authentication configuration");
    }
  }
}
