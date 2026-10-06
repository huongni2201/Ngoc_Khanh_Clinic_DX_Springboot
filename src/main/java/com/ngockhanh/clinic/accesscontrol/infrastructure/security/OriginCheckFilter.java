package com.ngockhanh.clinic.accesscontrol.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Cross-site request protection for cookie authentication: state-changing requests must come from
 * an allowed origin, taken from {@code Origin} or, when absent, from {@code Referer}. Requests with
 * neither header are rejected (ADR-0014).
 */
public final class OriginCheckFilter extends OncePerRequestFilter {
  private static final Set<String> STATE_CHANGING = Set.of("POST", "PUT", "PATCH", "DELETE");

  private final List<String> allowedOrigins;
  private final JsonSecurityErrorHandler errors;

  public OriginCheckFilter(List<String> allowedOrigins, JsonSecurityErrorHandler errors) {
    this.allowedOrigins = List.copyOf(allowedOrigins);
    this.errors = errors;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !STATE_CHANGING.contains(request.getMethod());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String origin = requestOrigin(request);
    if (origin == null || !allowedOrigins.contains(origin)) {
      errors.write(response, HttpStatus.FORBIDDEN, JsonSecurityErrorHandler.FORBIDDEN);
      return;
    }
    chain.doFilter(request, response);
  }

  private static String requestOrigin(HttpServletRequest request) {
    String origin = request.getHeader("Origin");
    if (origin != null) return origin;
    String referer = request.getHeader("Referer");
    if (referer == null) return null;
    try {
      URI uri = URI.create(referer);
      if (uri.getScheme() == null || uri.getHost() == null) return null;
      return uri.getScheme()
          + "://"
          + uri.getHost()
          + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
    } catch (IllegalArgumentException malformed) {
      return null;
    }
  }
}
