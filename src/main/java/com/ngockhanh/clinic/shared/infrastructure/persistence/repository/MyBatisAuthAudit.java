package com.ngockhanh.clinic.shared.infrastructure.persistence.repository;

import com.ngockhanh.clinic.shared.audit.AuthAudit;
import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import com.ngockhanh.clinic.shared.infrastructure.persistence.mapper.AuditMapper;
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
