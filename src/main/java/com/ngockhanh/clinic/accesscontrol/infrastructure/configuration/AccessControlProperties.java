package com.ngockhanh.clinic.accesscontrol.infrastructure.configuration;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code clinic.auth.*} settings. Invalid values fail application startup. Timeout limits are
 * validated by {@link com.ngockhanh.clinic.accesscontrol.domain.valueobject.SessionPolicy}.
 */
@ConfigurationProperties("clinic.auth")
public record AccessControlProperties(
    Duration idleTimeout,
    Duration absoluteTimeout,
    Boolean cookieSecure,
    List<String> allowedOrigins) {
  private static final String SECURE_COOKIE_NAME = "__Host-NKC_SESSION";
  private static final String LOCAL_COOKIE_NAME = "NKC_SESSION";

  public AccessControlProperties {
    idleTimeout = idleTimeout == null ? Duration.ofMinutes(30) : idleTimeout;
    absoluteTimeout = absoluteTimeout == null ? Duration.ofHours(8) : absoluteTimeout;
    cookieSecure = cookieSecure == null ? Boolean.TRUE : cookieSecure;
    allowedOrigins =
        allowedOrigins == null
            ? List.of()
            : allowedOrigins.stream()
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    if (allowedOrigins.contains("*"))
      throw new IllegalArgumentException("Wildcard allowed origin is not permitted");
  }

  /** {@code __Host-} cookies require Secure, so local HTTP uses the unprefixed name. */
  public String cookieName() {
    return Boolean.TRUE.equals(cookieSecure) ? SECURE_COOKIE_NAME : LOCAL_COOKIE_NAME;
  }
}
