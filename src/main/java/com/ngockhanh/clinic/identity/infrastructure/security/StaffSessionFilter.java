package com.ngockhanh.clinic.identity.infrastructure.security;

import com.ngockhanh.clinic.identity.application.usecase.*;
import com.ngockhanh.clinic.identity.application.port.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.util.*;

final class StaffSessionFilter extends OncePerRequestFilter {
  private final StaffAuthentication authentication;
  private final SecurityErrors errors;

  StaffSessionFilter(StaffAuthentication authentication, SecurityErrors errors) {
    this.authentication = authentication;
    this.errors = errors;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getServletPath();
    return "OPTIONS".equals(request.getMethod()) || Set.of("/api/v1/auth/csrf", "/api/v1/auth/staff/login",
        "/api/v1/auth/logout").contains(path);
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String id = null;
    if (request.getCookies() != null) {
      var cookies = Arrays.stream(request.getCookies()).filter(c -> "NKC_SESSION".equals(c.getName())).toList();
      if (cookies.size() > 1) {
        errors.write(response, AuthenticationFailure.invalid());
        return;
      }
      if (!cookies.isEmpty()) id = cookies.getFirst().getValue();
    }
    if (id != null) {
      try {
        var principal = authentication.authenticate(id);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        SecurityContextHolder.setContext(context);
      } catch (AuthenticationFailure ex) {
        errors.write(response, ex);
        return;
      }
    }
    try {
      chain.doFilter(request, response);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
