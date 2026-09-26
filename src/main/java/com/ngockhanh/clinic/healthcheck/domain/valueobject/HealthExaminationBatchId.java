package com.ngockhanh.clinic.healthcheck.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

public record HealthExaminationBatchId(UUID value) {
    public HealthExaminationBatchId {
        Objects.requireNonNull(value, "HealthExaminationBatchId must not be null");
    }
}
