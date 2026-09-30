package com.ngockhanh.clinic.shared.web;

import com.ngockhanh.clinic.shared.exception.ApplicationException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;

@Component
@RequiredArgsConstructor
public final class ApiResponseWriter {
  private final JsonMapper jsonMapper;

  public void write(HttpServletResponse response, ApplicationException exception) throws IOException {
    int status = statusFor(exception);
    response.setStatus(status);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.setHeader("Cache-Control", "no-store");
    if (exception.retryAfterSeconds() > 0) {
      response.setHeader("Retry-After", Long.toString(exception.retryAfterSeconds()));
    }
    jsonMapper.writeValue(response.getOutputStream(), ApiResponse.error(status, exception.getMessage()));
  }

  public void write(HttpServletResponse response, int status, String message) throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.setHeader("Cache-Control", "no-store");
    write(response.getOutputStream(), status, message);
  }

  public void write(OutputStream output, int status, String message) throws IOException {
    jsonMapper.writeValue(output, ApiResponse.error(status, message));
  }

  public int statusFor(ApplicationException exception) {
    return switch (exception.type()) {
      case INVALID_INPUT -> 400;
      case UNAUTHENTICATED -> 401;
      case ACCESS_DENIED -> 403;
      case RATE_LIMITED -> 429;
      case DEPENDENCY_UNAVAILABLE -> 503;
    };
  }
}
