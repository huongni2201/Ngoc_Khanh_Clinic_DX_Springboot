package com.ngockhanh.clinic.shared.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

public record AuditLogRecord(
        UUID id,
        Instant occurredAt,
        UUID actorUserId,
        String action,
        String entityType,
        String entityId,
        UUID patientId,
        UUID encounterId,
        UUID healthExaminationRecordId,
        UUID correlationId,
        String reason,
        String beforeJson,
        String afterJson,
        String ipAddress,
        String userAgent
) {
}
