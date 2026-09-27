package com.ngockhanh.clinic.healthcheck.application.port;

import java.util.List;
import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.application.query.ParticipantSummary;

public interface BatchParticipantSummaryQuery {
    List<ParticipantSummary> findByBatch(UUID batchId, int offset, int limit);
    long countByBatch(UUID batchId);
}
