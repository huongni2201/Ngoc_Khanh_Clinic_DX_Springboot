package com.ngockhanh.clinic.identity.infrastructure.security;

import com.ngockhanh.clinic.identity.application.port.AuthenticationFailure;
import com.ngockhanh.clinic.shared.web.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

final class SecurityErrors {
  private final JsonMapper json;

  SecurityErrors(JsonMapper json) {
    this.json = json;
  }

  void write(HttpServletResponse response, AuthenticationFailure error) throws IOException {
    response.setStatus(error.status());
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.setHeader("Cache-Control", "no-store");
    if (error.retryAfter() > 0) response.setHeader("Retry-After", Long.toString(error.retryAfter()));
    json.writeValue(response.getOutputStream(), new ApiError(error.status(), error.getMessage()));
  }
}
