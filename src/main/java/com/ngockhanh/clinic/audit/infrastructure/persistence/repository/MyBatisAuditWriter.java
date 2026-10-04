package com.ngockhanh.clinic.audit.infrastructure.persistence.repository;

import com.ngockhanh.clinic.audit.application.port.AuditWriter;
import com.ngockhanh.clinic.audit.infrastructure.persistence.mapper.AuditEventMapper;
import com.ngockhanh.clinic.audit.infrastructure.persistence.record.AuditEventRecord;
import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisAuditWriter implements AuditWriter {
  private final AuditEventMapper mapper;
  private final tools.jackson.databind.json.JsonMapper json;

  public void record(UUID actor, String action, String type, UUID id, Object before, Object after) {
    if (actor == null) throw new IllegalArgumentException("Audit actor is required");
    if (mapper.insert(
            new AuditEventRecord(
                UuidV7Generator.generate(),
                null,
                actor,
                action,
                type,
                id,
                null,
                null,
                json.writeValueAsString(
                    Map.of(
                        "before", before == null ? Map.of() : before,
                        "after", after == null ? Map.of() : after))))
        != 1) throw new IllegalStateException("Audit not saved");
  }
}
