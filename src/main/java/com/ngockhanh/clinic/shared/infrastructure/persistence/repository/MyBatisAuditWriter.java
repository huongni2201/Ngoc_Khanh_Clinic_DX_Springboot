package com.ngockhanh.clinic.shared.infrastructure.persistence.repository;

import com.ngockhanh.clinic.shared.audit.AuditWriter;
import com.ngockhanh.clinic.shared.infrastructure.persistence.mapper.AuditLogMapper;
import com.ngockhanh.clinic.shared.infrastructure.persistence.record.AuditLogRecord;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisAuditWriter implements AuditWriter {
  private final AuditLogMapper mapper;
  private final IdGenerator ids;
  private final tools.jackson.databind.json.JsonMapper json;

  public void record(UUID actor, String action, String type, UUID id, Object before, Object after) {
    if (actor == null) throw new IllegalArgumentException("Audit actor is required");
    if (mapper.insert(
            new AuditLogRecord(
                ids.next(),
                null,
                actor,
                action,
                type,
                id.toString(),
                null,
                null,
                null,
                null,
                null,
                before == null ? "{}" : json.writeValueAsString(before),
                after == null ? "{}" : json.writeValueAsString(after),
                null,
                null))
        != 1) throw new IllegalStateException("Audit not saved");
  }
}
