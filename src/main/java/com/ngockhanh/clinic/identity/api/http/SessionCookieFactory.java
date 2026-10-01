package com.ngockhanh.clinic.identity.api.http;

import java.time.Duration;

import org.springframework.http.ResponseCookie;

public final class SessionCookieFactory {

    public static final String SESSION_COOKIE_NAME = "NKC_SESSION";

    private final boolean secure;

    public SessionCookieFactory(boolean secure) {
        this.secure = secure;
    }

    public ResponseCookie create(String sessionId, Duration maxAge) {
        return ResponseCookie.from(SESSION_COOKIE_NAME, sessionId)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    public ResponseCookie clear() {
        return create("", Duration.ZERO);
    }
}
