package com.ngockhanh.clinic.healthcheck.application.importparticipant;

import java.time.LocalDate;
import java.util.UUID;

public record ParticipantImportBatchContext(UUID id, UUID organizationId, LocalDate startDate) {
}
