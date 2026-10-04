package com.ngockhanh.clinic.identity.api.controller;

import com.ngockhanh.clinic.identity.api.http.SessionCookieFactory;
import com.ngockhanh.clinic.identity.api.http.TrustedProxyClientIpResolver;
import com.ngockhanh.clinic.identity.api.request.LoginRequest;
import com.ngockhanh.clinic.identity.application.command.LoginCommand;
import com.ngockhanh.clinic.identity.application.command.LogoutAllSessionsCommand;
import com.ngockhanh.clinic.identity.application.command.LogoutSessionCommand;
import com.ngockhanh.clinic.identity.application.query.GetCsrfTokenQuery;
import com.ngockhanh.clinic.identity.application.query.GetSessionQuery;
import com.ngockhanh.clinic.identity.application.query.UserPrincipal;
import com.ngockhanh.clinic.identity.application.response.CsrfResponse;
import com.ngockhanh.clinic.identity.application.response.LoginResult;
import com.ngockhanh.clinic.identity.application.response.UserSessionResponse;
import com.ngockhanh.clinic.identity.application.usecase.GetCsrfTokenUseCase;
import com.ngockhanh.clinic.identity.application.usecase.GetSessionUseCase;
import com.ngockhanh.clinic.identity.application.usecase.LoginUseCase;
import com.ngockhanh.clinic.identity.application.usecase.LogoutAllSessionsUseCase;
import com.ngockhanh.clinic.identity.application.usecase.LogoutSessionUseCase;
import com.ngockhanh.clinic.shared.web.ApiResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {
  private static final String SESSION_COOKIE = SessionCookieFactory.SESSION_COOKIE_NAME;

  private final GetCsrfTokenUseCase getCsrfToken;
  private final LoginUseCase loginUser;
  private final GetSessionUseCase getSession;
  private final LogoutSessionUseCase logoutSession;
  private final LogoutAllSessionsUseCase logoutAllSessions;
  private final SessionCookieFactory sessionCookies;
  private final TrustedProxyClientIpResolver clientIpResolver;
  private final CsrfTokenRepository csrf;
  private final Clock clock;

  /**
   * Returns a masked CSRF token and header name for login and other unsafe requests. Send the
   * returned token in the named header and retain the CSRF cookie set by Spring Security.
   *
   * @param token the CSRF token supplied by Spring Security
   * @return the CSRF token response and HTTP status
   */
  @GetMapping("/csrf")
  public ResponseEntity<ApiResponse<CsrfResponse>> csrf(CsrfToken token) {
    log.debug("Issuing CSRF token");
    GetCsrfTokenQuery query = new GetCsrfTokenQuery(token.getToken(), token.getHeaderName());
    CsrfResponse response = getCsrfToken.execute(query);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(HttpStatus.OK.value(), "CSRF token", response));
  }

  /**
   * Authenticates an account's username and password and creates a server-side session. Requires a
   * current CSRF token; returns the account session view and sets the opaque session ID in an
   * HttpOnly cookie. A supplied session cookie is replaced after successful login.
   *
   * @param body the submitted account credentials
   * @param request the HTTP request containing cookies and peer address
   * @param response the HTTP response used to clear the CSRF cookie
   * @return the account session response and newly issued session cookie
   */
  @PostMapping("/login")
  public ResponseEntity<ApiResponse<UserSessionResponse>> login(
      @Valid @RequestBody LoginRequest body,
      HttpServletRequest request,
      HttpServletResponse response) {
    UUID correlationId = UUID.randomUUID();
    LoginCommand command =
        new LoginCommand(
            body.username(),
            body.password(),
            clientIpResolver.resolve(request),
            sessionIds(request),
            correlationId);
    LoginResult login = loginUser.execute(command);
    csrf.saveToken(null, request, response);
    Duration cookieAge = Duration.between(clock.instant(), login.response().absoluteExpiresAt());
    if (cookieAge.isNegative()) {
      cookieAge = Duration.ZERO;
    }
    log.info("User login response prepared correlationId={}", correlationId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(
            HttpHeaders.SET_COOKIE, sessionCookies.create(login.sessionId(), cookieAge).toString())
        .body(ApiResponse.success(HttpStatus.OK.value(), "Login successful", login.response()));
  }

  /**
   * Returns the authenticated account and role grants in the current session snapshot. Requires a
   * valid NKC_SESSION cookie; the response contains the session role snapshot and expiration times.
   *
   * @param principal the authenticated account principal
   * @return the current account session response
   */
  @GetMapping("/me")
  public ResponseEntity<ApiResponse<UserSessionResponse>> me(
      @AuthenticationPrincipal UserPrincipal principal) {
    log.debug("Reading authenticated userId={}", principal.userId());
    GetSessionQuery query = new GetSessionQuery(principal);
    UserSessionResponse response = getSession.execute(query);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(HttpStatus.OK.value(), "Current user", response));
  }

  /**
   * Revokes the session identified by the NKC_SESSION cookie and clears session and CSRF cookies.
   * Requires a CSRF token; succeeds with 204 when the session is absent or already expired.
   *
   * @param request the HTTP request containing the session and CSRF cookies
   * @param response the HTTP response used to clear both cookies
   * @return an empty 204 response after session revocation
   */
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
    LogoutSessionCommand command = new LogoutSessionCommand(sessionIds(request), UUID.randomUUID());
    logoutSession.execute(command);
    return clear(request, response);
  }

  /**
   * Revokes every session belonging to the authenticated account and clears this browser's cookies.
   * Requires an authenticated session and CSRF token; returns 204 after all sessions are revoked.
   *
   * @param principal the authenticated account principal
   * @param request the HTTP request containing this browser's cookies
   * @param response the HTTP response used to clear this browser's cookies
   * @return an empty 204 response after all sessions are revoked
   */
  @PostMapping("/logout-all")
  public ResponseEntity<Void> logoutAll(
      @AuthenticationPrincipal UserPrincipal principal,
      HttpServletRequest request,
      HttpServletResponse response) {
    LogoutAllSessionsCommand command =
        new LogoutAllSessionsCommand(principal.userId(), UUID.randomUUID());
    logoutAllSessions.execute(command);
    return clear(request, response);
  }

  private ResponseEntity<Void> clear(HttpServletRequest request, HttpServletResponse response) {
    csrf.saveToken(null, request, response);
    return ResponseEntity.noContent()
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header(HttpHeaders.SET_COOKIE, sessionCookies.clear().toString())
        .build();
  }

  private List<String> sessionIds(HttpServletRequest request) {
    if (request.getCookies() == null) {
      return List.of();
    }
    return Arrays.stream(request.getCookies())
        .filter(cookie -> SESSION_COOKIE.equals(cookie.getName()))
        .map(Cookie::getValue)
        .toList();
  }
}
