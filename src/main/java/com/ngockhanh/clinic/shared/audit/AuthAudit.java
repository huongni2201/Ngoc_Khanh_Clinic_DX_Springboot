package com.ngockhanh.clinic.shared.audit;
import java.time.Instant;
import java.util.UUID;
public interface AuthAudit {
    void record(UUID userId, String action, Instant occurredAt, UUID correlationId);
}
