package com.ngockhanh.clinic.audit.infrastructure.persistence.repository;

import com.ngockhanh.clinic.audit.application.port.AuditWriter;
import com.ngockhanh.clinic.audit.infrastructure.persistence.mapper.AuditEventMapper;
import com.ngockhanh.clinic.audit.infrastructure.persistence.record.AuditEventRecord;
import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
@RequiredArgsConstructor
public class MyBatisAuditWriter implements AuditWriter {
  private final AuditEventMapper mapper;
  private final JsonMapper json;

  @Override
  public void record(UUID actor, String action, String type, UUID id, Object before, Object after) {
    recordEvent(
        actor,
        action,
        type,
        id,
        null,
        null,
        Map.of(
            "before", before == null ? Map.of() : before,
            "after", after == null ? Map.of() : after));
  }

  @Override
  public void record(UUID userId, String action, Instant occurredAt, UUID correlationId) {
    if (occurredAt == null)
      throw new IllegalArgumentException("Authentication audit time is required");
    recordEvent(userId, action, "ACCOUNT", userId, occurredAt, correlationId, Map.of());
  }

  private void recordEvent(
      UUID actor,
      String action,
      String resourceType,
      UUID resourceId,
      Instant occurredAt,
      UUID correlationId,
      Object metadata) {
    if (actor == null) throw new IllegalArgumentException("Audit actor is required");
    var event =
        AuditEventRecord.builder()
            .id(UuidV7Generator.generate())
            .occurredAt(occurredAt)
            .actorAccountId(actor)
            .action(action)
            .resourceType(resourceType)
            .resourceId(resourceId)
            .correlationId(correlationId)
            .metadata(json.writeValueAsString(metadata))
            .build();
    if (mapper.insert(event) != 1) throw new IllegalStateException("Audit not saved");
  }
}
