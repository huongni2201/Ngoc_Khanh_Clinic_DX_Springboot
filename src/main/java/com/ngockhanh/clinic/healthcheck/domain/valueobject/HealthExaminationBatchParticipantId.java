package com.ngockhanh.clinic.healthcheck.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

public record HealthExaminationBatchParticipantId(UUID value) {
    public HealthExaminationBatchParticipantId {
        Objects.requireNonNull(value, "HealthExaminationBatchParticipantId must not be null");
    }
}
