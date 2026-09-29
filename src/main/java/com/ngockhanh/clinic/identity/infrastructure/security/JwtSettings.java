package com.ngockhanh.clinic.identity.infrastructure.security;

public record JwtSettings(String issuer, String audience, String base64Key) {
  public JwtSettings {
    if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
      throw new IllegalArgumentException("Invalid JWT issuer or audience configuration");
    }
    base64Key = base64Key == null ? "" : base64Key;
  }

  @Override
  public String toString() {
    return "JwtSettings[issuer=" + issuer + ", audience=" + audience + "]";
  }
}
