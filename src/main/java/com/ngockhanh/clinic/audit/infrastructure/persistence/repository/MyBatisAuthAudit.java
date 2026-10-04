package com.ngockhanh.clinic.audit.infrastructure.persistence.repository;

import com.ngockhanh.clinic.audit.application.port.AuthAudit;
import com.ngockhanh.clinic.audit.infrastructure.persistence.mapper.AuditMapper;
import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisAuthAudit implements AuthAudit {
  private final AuditMapper mapper;

  public MyBatisAuthAudit(AuditMapper mapper) {
    this.mapper = mapper;
  }

  public void record(UUID userId, String action, Instant at, UUID correlation) {
    mapper.insert(UuidV7Generator.generate(), userId, action, at, correlation);
  }
}
