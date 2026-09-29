package com.ngockhanh.clinic.identity.application.exception;

import com.ngockhanh.clinic.shared.exception.ApplicationException;

public final class AuthenticationFailure extends ApplicationException {
    private AuthenticationFailure(Type type, String message) {
        super(type, message);
    }

    private AuthenticationFailure(Type type, String message, long retryAfterSeconds) {
        super(type, message, retryAfterSeconds);
    }

    private AuthenticationFailure(Type type, String message, Throwable cause) {
        super(type, message, cause);
    }

    public static AuthenticationFailure invalid() {
        return new AuthenticationFailure(Type.UNAUTHENTICATED, "Invalid credentials or session");
    }

    public static AuthenticationFailure invalidRequest() {
        return new AuthenticationFailure(Type.INVALID_INPUT, "Invalid login request");
    }

    public static AuthenticationFailure invalidCookie() {
        return new AuthenticationFailure(Type.INVALID_INPUT, "Invalid session cookie");
    }

    public static AuthenticationFailure rateLimited(long retryAfterSeconds) {
        return new AuthenticationFailure(Type.RATE_LIMITED, "Too many login attempts", retryAfterSeconds);
    }

    public static AuthenticationFailure unavailable(Throwable cause) {
        return new AuthenticationFailure(Type.DEPENDENCY_UNAVAILABLE, "Authentication service unavailable", cause);
    }

    public static AuthenticationFailure forbidden() {
        return new AuthenticationFailure(Type.ACCESS_DENIED, "Access denied");
    }
}
