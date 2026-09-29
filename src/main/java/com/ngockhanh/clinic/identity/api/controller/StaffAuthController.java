package com.ngockhanh.clinic.identity.api.controller;

import com.ngockhanh.clinic.identity.api.configuration.AuthApiSettings;
import com.ngockhanh.clinic.identity.api.request.StaffLoginRequest;
import com.ngockhanh.clinic.identity.application.command.LogoutAllStaffSessionsCommand;
import com.ngockhanh.clinic.identity.application.command.LogoutStaffSessionCommand;
import com.ngockhanh.clinic.identity.application.command.StaffLoginCommand;
import com.ngockhanh.clinic.identity.application.query.GetCsrfTokenQuery;
import com.ngockhanh.clinic.identity.application.query.GetStaffSessionQuery;
import com.ngockhanh.clinic.identity.application.query.access.StaffPrincipal;
import com.ngockhanh.clinic.identity.application.response.CsrfResponse;
import com.ngockhanh.clinic.identity.application.response.StaffLoginResult;
import com.ngockhanh.clinic.identity.application.response.StaffSessionResponse;
import com.ngockhanh.clinic.identity.application.usecase.GetCsrfTokenUseCase;
import com.ngockhanh.clinic.identity.application.usecase.GetStaffSessionUseCase;
import com.ngockhanh.clinic.identity.application.usecase.LogoutAllStaffSessionsUseCase;
import com.ngockhanh.clinic.identity.application.usecase.LogoutStaffSessionUseCase;
import com.ngockhanh.clinic.identity.application.usecase.StaffLoginUseCase;
import com.ngockhanh.clinic.shared.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class StaffAuthController {
  private static final String SESSION_COOKIE = "NKC_SESSION";

  private final GetCsrfTokenUseCase getCsrfToken;
  private final StaffLoginUseCase staffLogin;
  private final GetStaffSessionUseCase getStaffSession;
  private final LogoutStaffSessionUseCase logoutStaffSession;
  private final LogoutAllStaffSessionsUseCase logoutAllStaffSessions;
  private final AuthApiSettings settings;
  private final CsrfTokenRepository csrf;
  private final Clock clock;

  /**
   * Returns the CSRF token and header name required by login and other unsafe requests.
   * The token is also set in the CSRF cookie by Spring Security; clients must echo it in the returned header.
   */
  @GetMapping("/csrf")
  public ResponseEntity<ApiResponse<CsrfResponse>> csrf(CsrfToken token) {
    log.debug("Issuing CSRF token");
    var response = getCsrfToken.execute(new GetCsrfTokenQuery(token.getToken(), token.getHeaderName()));
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(200, "CSRF token", response));
  }

  /**
   * Authenticates a staff username and password and creates a server-side session.
   * Requires a current CSRF token; returns the staff session view and sets the opaque session ID in an HttpOnly cookie.
   * A supplied session cookie is replaced after successful login; passwords and JWTs are never returned.
   */
  @PostMapping("/staff/login")
  public ResponseEntity<ApiResponse<StaffSessionResponse>> login(
      @Valid @RequestBody StaffLoginRequest body,
      HttpServletRequest request,
      HttpServletResponse response) {
    UUID correlationId = UUID.randomUUID();
    StaffLoginResult login = staffLogin.execute(new StaffLoginCommand(
        body.username(), body.password(), clientIp(request), sessionIds(request), correlationId));
    csrf.saveToken(null, request, response);
    Duration cookieAge = Duration.between(clock.instant(), login.response().absoluteExpiresAt());
    if (cookieAge.isNegative()) {
      cookieAge = Duration.ZERO;
    }
    log.info("Staff login response prepared correlationId={}", correlationId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.SET_COOKIE, sessionCookie(login.sessionId(), cookieAge).toString())
        .body(ApiResponse.success(200, "Login successful", login.response()));
  }

  /**
   * Returns the authenticated staff member and role assignments in the current session snapshot.
   * Requires a valid NKC_SESSION cookie; the response includes the idle and absolute session expiration times.
   */
  @GetMapping("/me")
  public ResponseEntity<ApiResponse<StaffSessionResponse>> me(
      @AuthenticationPrincipal StaffPrincipal principal) {
    log.debug("Reading authenticated staff userId={}", principal.userId());
    var response = getStaffSession.execute(new GetStaffSessionQuery(principal));
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(200, "Current staff", response));
  }

  /**
   * Revokes the session identified by the NKC_SESSION cookie and clears session and CSRF cookies.
   * Requires a CSRF token; succeeds with 204 when the session is absent or already expired.
   */
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
    logoutStaffSession.execute(new LogoutStaffSessionCommand(sessionIds(request), UUID.randomUUID()));
    return clear(request, response);
  }

  /**
   * Revokes every session belonging to the authenticated staff user and clears this browser's cookies.
   * Requires an authenticated session and a CSRF token; returns 204 after Redis revocation succeeds.
   */
  @PostMapping("/logout-all")
  public ResponseEntity<Void> logoutAll(
      @AuthenticationPrincipal StaffPrincipal principal,
      HttpServletRequest request,
      HttpServletResponse response) {
    logoutAllStaffSessions.execute(new LogoutAllStaffSessionsCommand(principal.userId(), UUID.randomUUID()));
    return clear(request, response);
  }

  private ResponseEntity<Void> clear(HttpServletRequest request, HttpServletResponse response) {
    csrf.saveToken(null, request, response);
    return ResponseEntity.noContent()
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO).toString())
        .build();
  }

  private ResponseCookie sessionCookie(String value, Duration age) {
    return ResponseCookie.from(SESSION_COOKIE, value)
        .httpOnly(true)
        .secure(settings.secureCookie())
        .sameSite("Lax")
        .path("/")
        .maxAge(age)
        .build();
  }

  private List<String> sessionIds(HttpServletRequest request) {
    if (request.getCookies() == null) {
      return List.of();
    }
    return Arrays.stream(request.getCookies())
        .filter(cookie -> SESSION_COOKIE.equals(cookie.getName()))
        .map(cookie -> cookie.getValue())
        .toList();
  }

  private String clientIp(HttpServletRequest request) {
    String peer = request.getRemoteAddr();
    if (!settings.trustedProxies().contains(peer)) {
      return peer;
    }
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded == null || forwarded.length() > 1024) {
      return peer;
    }
    String[] chain = forwarded.split(",");
    for (int i = chain.length - 1; i >= 0; i--) {
      try {
        peer = InetAddress.ofLiteral(chain[i].strip()).getHostAddress();
      } catch (IllegalArgumentException invalidAddress) {
        return request.getRemoteAddr();
      }
      if (!settings.trustedProxies().contains(peer)) {
        return peer;
      }
    }
    return peer;
  }
}
