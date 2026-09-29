package com.ngockhanh.clinic.identity.infrastructure.configuration;

import java.util.List;

public record AuthSecuritySettings(boolean secureCookie, boolean businessAccess, List<String> allowedOrigins) {
  public AuthSecuritySettings {
    allowedOrigins = List.copyOf(allowedOrigins);
    if (allowedOrigins.contains("*")) {
      throw new IllegalArgumentException("Authentication CORS origins must be explicit");
    }
  }
}
