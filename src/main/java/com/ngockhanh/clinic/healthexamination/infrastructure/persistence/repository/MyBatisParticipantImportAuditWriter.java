package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository;

import com.ngockhanh.clinic.audit.application.port.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantImportAuditWriter;
import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantImportAuditWriter.AuditEntry;
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
