package com.ngockhanh.clinic.healthexamination.infrastructure.persistence;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ngockhanh.clinic.audit.application.port.AuditWriter;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportStatus;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository.MyBatisParticipantImportAuditWriter;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MyBatisParticipantImportAuditWriterTest {
  @Test
  void writesTheActorAndStatusTransitionWithoutRosterData() {
    AuditWriter auditWriter = mock(AuditWriter.class);
    MyBatisParticipantImportAuditWriter writer =
        new MyBatisParticipantImportAuditWriter(auditWriter);
    AuditEntry entry =
        new AuditEntry(
            id(1),
            id(2),
            Instant.parse("2026-09-30T00:00:00Z"),
            "PARTICIPANT_ROSTER_IMPORT_CONFIRMED",
            id(3),
            new Snapshot(ImportStatus.VALIDATED, 0, 1),
            new Snapshot(ImportStatus.CONFIRMED, 1, 1));

    writer.record(entry);

    verify(auditWriter)
        .record(
            id(2),
            "PARTICIPANT_ROSTER_IMPORT_CONFIRMED",
            "HEALTH_EXAMINATION_IMPORT_JOB",
            id(3),
            new Snapshot(ImportStatus.VALIDATED, 0, 1),
            new Snapshot(ImportStatus.CONFIRMED, 1, 1));
  }

  private static UUID id(long value) {
    return new UUID(0, value);
  }
}
