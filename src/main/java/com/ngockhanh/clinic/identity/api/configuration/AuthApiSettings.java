package com.ngockhanh.clinic.identity.api.configuration;

import java.util.List;

public record AuthApiSettings(boolean secureCookie, List<String> trustedProxies) {
  public AuthApiSettings {
    trustedProxies = List.copyOf(trustedProxies);
  }
}
