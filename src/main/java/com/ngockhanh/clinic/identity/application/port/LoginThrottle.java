package com.ngockhanh.clinic.identity.application.port;

public interface LoginThrottle {
    record CheckResult(boolean allowed, long retryAfterSeconds) {
    }

    CheckResult check(String username, String ip);

    void failed(String username);
}
