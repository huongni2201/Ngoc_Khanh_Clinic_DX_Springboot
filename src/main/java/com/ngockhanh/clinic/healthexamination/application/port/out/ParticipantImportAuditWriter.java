package com.ngockhanh.clinic.healthexamination.application.port.out;

import com.ngockhanh.clinic.healthexamination.domain.enums.ImportStatus;
import java.time.Instant;
import java.util.UUID;

public interface ParticipantImportAuditWriter {
  void record(AuditEntry entry);

  record AuditEntry(
      UUID id,
      UUID actorUserId,
      Instant occurredAt,
      String action,
      UUID importJobId,
      Snapshot before,
      Snapshot after) {
    public AuditEntry {
      if (id == null
          || actorUserId == null
          || occurredAt == null
          || action == null
          || action.isBlank()
          || importJobId == null
          || before == null
          || after == null) {
        throw new IllegalArgumentException("Import audit details are required");
      }
    }
  }

  record Snapshot(ImportStatus status, long rowVersion, int totalRows) {
    public Snapshot {
      if (rowVersion < 0 || totalRows < 0)
        throw new IllegalArgumentException("Invalid import audit snapshot");
    }
  }
}
