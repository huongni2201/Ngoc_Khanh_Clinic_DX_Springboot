package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository;

import com.ngockhanh.clinic.audit.application.port.AuditWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisParticipantImportAuditWriter implements ParticipantImportAuditWriter {
  private final AuditWriter auditWriter;

  @Override
  public void record(AuditEntry entry) {
    auditWriter.record(
        entry.actorUserId(),
        entry.action(),
        "HEALTH_EXAMINATION_IMPORT_JOB",
        entry.importJobId(),
        entry.before(),
        entry.after());
  }
}
