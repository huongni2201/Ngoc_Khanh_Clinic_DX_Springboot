package com.ngockhanh.clinic.audit.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

public record AuditEventRecord(
    UUID id,
    Instant occurredAt,
    UUID actorAccountId,
    String action,
    String resourceType,
    UUID resourceId,
    UUID departmentId,
    UUID correlationId,
    String metadata) {}
