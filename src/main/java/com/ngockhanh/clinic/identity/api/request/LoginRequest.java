package com.ngockhanh.clinic.identity.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record LoginRequest(
    @NotBlank @Size(max = 150) String username, @NotEmpty @Size(max = 72) String password) {
  @Override
  public String toString() {
    return "LoginRequest[redacted]";
  }

  public static class LoginRequestBuilder {
    @Override
    public String toString() {
      return "LoginRequestBuilder[redacted]";
    }
  }
}
