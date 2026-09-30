package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

public record ImportAuditLogRecord(
        UUID id,
        Instant occurredAt,
        UUID actorUserId,
        String action,
        String entityId,
        String beforeJson,
        String afterJson) {
}
