package com.ngockhanh.clinic.accesscontrol.infrastructure.security;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.usecase.AuthenticateSessionUseCase;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.DependencyUnavailableException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates each request from the session cookie. Requests without the cookie continue
 * anonymously and are then judged by the authorization rules. Sign-in and sign-out handle the
 * cookie themselves, so an expired cookie never blocks them.
 */
@Slf4j
public final class SessionCookieAuthenticationFilter extends OncePerRequestFilter {
  private static final List<String> SELF_HANDLED_PATHS =
      List.of("/api/v1/auth/login", "/api/v1/auth/logout");

  private final AuthenticateSessionUseCase authenticate;
  private final String cookieName;
  private final JsonSecurityErrorHandler errors;

  public SessionCookieAuthenticationFilter(
      AuthenticateSessionUseCase authenticate, String cookieName, JsonSecurityErrorHandler errors) {
    this.authenticate = authenticate;
    this.cookieName = cookieName;
    this.errors = errors;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    return "POST".equals(request.getMethod()) && SELF_HANDLED_PATHS.contains(path);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    List<String> sessionIds = sessionIds(request);
    if (sessionIds.isEmpty()) {
      chain.doFilter(request, response);
      return;
    }
    if (sessionIds.size() > 1) {
      errors.write(response, HttpStatus.UNAUTHORIZED, JsonSecurityErrorHandler.UNAUTHENTICATED);
      return;
    }
    UserPrincipal principal;
    try {
      principal = authenticate.execute(sessionIds.getFirst());
    } catch (ApplicationException rejected) {
      errors.write(response, HttpStatus.UNAUTHORIZED, JsonSecurityErrorHandler.UNAUTHENTICATED);
      return;
    } catch (DependencyUnavailableException unavailable) {
      log.warn("Session store unavailable during request authentication");
      errors.write(response, HttpStatus.SERVICE_UNAVAILABLE, "Service is temporarily unavailable");
      return;
    }
    SecurityContext context = SecurityContextHolder.getContextHolderStrategy().createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities(principal)));
    SecurityContextHolder.getContextHolderStrategy().setContext(context);
    chain.doFilter(request, response);
  }

  private List<String> sessionIds(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies(); // servlet API contract returns an array
    if (cookies == null) return List.of();
    return Arrays.stream(cookies)
        .filter(cookie -> cookieName.equals(cookie.getName()))
        .map(Cookie::getValue)
        .toList();
  }

  /**
   * Account-type authorities are derived only from the account type; role and permission codes are
   * prefixed so that no code can impersonate them (ADR-0014).
   */
  static List<GrantedAuthority> authorities(UserPrincipal principal) {
    List<GrantedAuthority> authorities = new ArrayList<>();
    authorities.add(new SimpleGrantedAuthority("ACCOUNT_" + principal.principalType()));
    for (UserPrincipal.Assignment role : principal.roleAssignments()) {
      authorities.add(new SimpleGrantedAuthority("ROLE_" + role.roleCode()));
      role.permissions()
          .forEach(permission -> authorities.add(new SimpleGrantedAuthority("PERM_" + permission)));
    }
    return authorities;
  }
}
