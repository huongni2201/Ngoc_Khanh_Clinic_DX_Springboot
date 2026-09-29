package com.ngockhanh.clinic.identity.infrastructure.configuration;

import java.util.List;

public record AuthHttpSettings(
        boolean secureCookie,
        boolean businessAccess,
        List<String> allowedOrigins,
        List<String> trustedProxies) {

    public AuthHttpSettings {
        allowedOrigins = List.copyOf(allowedOrigins);
        trustedProxies = List.copyOf(trustedProxies);
        if (allowedOrigins.contains("*")) {
            throw new IllegalArgumentException("Authentication CORS origins must be explicit");
        }
    }
}
