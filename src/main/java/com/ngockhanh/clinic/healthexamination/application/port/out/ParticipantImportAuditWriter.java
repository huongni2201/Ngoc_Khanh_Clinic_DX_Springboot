package com.ngockhanh.clinic.healthexamination.application.port.out;

import java.time.Instant;
import java.util.UUID;

public interface ParticipantImportAuditWriter {
    void record(AuditEntry entry);

    record AuditEntry(UUID id, UUID actorUserId, Instant occurredAt, String action, UUID importJobId,
                      String beforeJson, String afterJson) {
        public AuditEntry {
            if (id == null || actorUserId == null || occurredAt == null || action == null || action.isBlank()
                    || importJobId == null || beforeJson == null || afterJson == null) {
                throw new IllegalArgumentException("Import audit details are required");
            }
        }
    }
}
