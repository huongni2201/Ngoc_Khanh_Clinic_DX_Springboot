package com.ngockhanh.clinic.identity.application.port;

import java.time.Instant;
import java.time.Duration;
import java.util.UUID;

public interface SessionStore {
    record Stored(UUID userId, String jwt, long generation, Instant absoluteExpiresAt) {
        @Override
        public String toString() {
            return "Stored[userId=" + userId + "]";
        }
    }

    long generation(UUID userId);

    boolean create(String sessionId, Stored session, Duration idle, Instant now);

    Stored find(String sessionId);

    Instant touch(String sessionId, Stored expected, Duration idle, Instant now);

    void delete(String sessionId);

    void revokeAll(UUID userId);
}
