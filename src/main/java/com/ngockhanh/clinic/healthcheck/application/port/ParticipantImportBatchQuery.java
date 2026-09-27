package com.ngockhanh.clinic.healthcheck.application.port;

import java.util.Optional;
import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.application.importparticipant.ParticipantImportBatchContext;

public interface ParticipantImportBatchQuery {
    Optional<ParticipantImportBatchContext> findById(UUID batchId);
}
