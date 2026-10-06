package com.ngockhanh.clinic.accesscontrol.api.http;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.web.util.WebUtils;

/**
 * Session cookie: HttpOnly, SameSite=Lax, Path=/, no Domain and no Max-Age, so it ends with the
 * browser session; the server enforces the real expiry (ADR-0014).
 */
public final class SessionCookieFactory {
  private final String cookieName;
  private final Boolean secure;

  public SessionCookieFactory(String cookieName, Boolean secure) {
    this.cookieName = cookieName;
    this.secure = secure;
  }

  /** Cookie carrying a new session ID. */
  public ResponseCookie create(String sessionId) {
    return builder(sessionId).build();
  }

  /** Cookie that removes the session cookie from the browser. */
  public ResponseCookie clear() {
    return builder("").maxAge(0).build();
  }

  /** Session ID sent by the browser, or null when absent. */
  public String read(HttpServletRequest request) {
    Cookie cookie = WebUtils.getCookie(request, cookieName);
    return cookie == null ? null : cookie.getValue();
  }

  private ResponseCookie.ResponseCookieBuilder builder(String value) {
    return ResponseCookie.from(cookieName, value)
        .httpOnly(true)
        .secure(Boolean.TRUE.equals(secure))
        .sameSite("Lax")
        .path("/");
  }
}
