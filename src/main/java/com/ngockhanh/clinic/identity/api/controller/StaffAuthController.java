package com.ngockhanh.clinic.identity.api.controller;

import com.ngockhanh.clinic.identity.application.usecase.*;
import com.ngockhanh.clinic.identity.application.port.*;
import com.ngockhanh.clinic.identity.application.query.access.StaffPrincipal;
import com.ngockhanh.clinic.shared.web.ApiResponse;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import com.ngockhanh.clinic.identity.api.request.StaffLoginRequest;
import com.ngockhanh.clinic.identity.api.response.*;
import com.ngockhanh.clinic.identity.application.command.StaffLoginCommand;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.net.InetAddress;
import java.util.*;

@RestController
@RequestMapping("/api/v1/auth")
public class StaffAuthController {
  private final StaffAuthentication auth;
  private final AuthSettings settings;
  private final CsrfTokenRepository csrf;
  private final Clock clock;

  public StaffAuthController(StaffAuthentication auth, AuthSettings settings, CsrfTokenRepository csrf, Clock clock) {
    this.auth = auth;
    this.settings = settings;
    this.csrf = csrf;
    this.clock = clock;
  }

  @GetMapping("/csrf")
  public ResponseEntity<ApiResponse<CsrfResponse>> csrf(CsrfToken token) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(new ApiResponse<>(200, "CSRF token", new CsrfResponse(token.getToken(), token.getHeaderName())));
  }

  @PostMapping("/staff/login")
  public ResponseEntity<ApiResponse<StaffSessionResponse>> login(@Valid @RequestBody StaffLoginRequest body,
                                                                 HttpServletRequest request, HttpServletResponse response) {
    var login = auth.login(new StaffLoginCommand(body.username(), body.password(), clientIp(request), sessionId(request), UUID.randomUUID()));
    csrf.saveToken(null, request, response);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .header(HttpHeaders.SET_COOKIE, cookie(login.sessionId(),
            Duration.between(clock.instant(), login.principal().absoluteExpiresAt()).isNegative()
                ? Duration.ZERO : Duration.between(clock.instant(), login.principal().absoluteExpiresAt())).toString())
        .body(new ApiResponse<>(200, "Login successful", StaffSessionResponse.from(login.principal())));
  }

  @GetMapping("/me")
  public ResponseEntity<ApiResponse<StaffSessionResponse>> me(@AuthenticationPrincipal StaffPrincipal principal) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ApiResponse<>(200, "Current staff", StaffSessionResponse.from(principal)));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
    auth.logout(sessionId(request), UUID.randomUUID());
    return clear(request, response);
  }

  @PostMapping("/logout-all")
  public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal StaffPrincipal principal,
                                        HttpServletRequest request, HttpServletResponse response) {
    auth.logoutAll(principal.userId(), UUID.randomUUID());
    return clear(request, response);
  }

  private ResponseEntity<Void> clear(HttpServletRequest request, HttpServletResponse response) {
    csrf.saveToken(null, request, response);
    return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).build();
  }

  private ResponseCookie cookie(String value, Duration age) {
    return ResponseCookie.from("NKC_SESSION", value).httpOnly(true).secure(settings.secureCookie())
        .sameSite("Lax").path("/").maxAge(age).build();
  }

  private String sessionId(HttpServletRequest request) {
    if (request.getCookies() == null) return null;
    var found = Arrays.stream(request.getCookies()).filter(c -> "NKC_SESSION".equals(c.getName())).toList();
    if (found.size() > 1) throw new AuthenticationFailure(400, "Invalid session cookie");
    return found.isEmpty() ? null : found.getFirst().getValue();
  }

  private String clientIp(HttpServletRequest request) {
    String peer = request.getRemoteAddr();
    if (!settings.trustedProxies().contains(peer)) return peer;
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded == null || forwarded.length() > 1024) return peer;
    String[] chain = forwarded.split(",");
    for (int i = chain.length - 1; i >= 0; i--) {
      try {
        peer = InetAddress.ofLiteral(chain[i].strip()).getHostAddress();
      } catch (IllegalArgumentException invalid) {
        return request.getRemoteAddr();
      }
      if (!settings.trustedProxies().contains(peer)) return peer;
    }
    return peer;
  }
}
