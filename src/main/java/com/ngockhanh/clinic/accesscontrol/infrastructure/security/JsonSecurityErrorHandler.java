package com.ngockhanh.clinic.accesscontrol.infrastructure.security;

import com.ngockhanh.clinic.shared.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

/** Writes security failures with the shared {@link ApiResponse} error envelope. */
@RequiredArgsConstructor
public final class JsonSecurityErrorHandler
    implements AuthenticationEntryPoint, AccessDeniedHandler {
  static final String UNAUTHENTICATED = "Authentication is required";
  static final String FORBIDDEN = "You are not authorized to perform this action";

  private final JsonMapper json;

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException failure)
      throws IOException {
    write(response, HttpStatus.UNAUTHORIZED, UNAUTHENTICATED);
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException denied)
      throws IOException {
    write(response, HttpStatus.FORBIDDEN, FORBIDDEN);
  }

  /** Writes an error response from a filter, outside Spring MVC exception handling. */
  public void write(HttpServletResponse response, HttpStatus status, String message)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.setHeader("Cache-Control", "no-store");
    json.writeValue(response.getOutputStream(), ApiResponse.error(status.value(), message));
  }
}
