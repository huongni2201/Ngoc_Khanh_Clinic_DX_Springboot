package com.ngockhanh.clinic.diagnostics.infrastructure.persistence.record;

import java.util.UUID;

public record SpecimenServiceRequestRecord(
        UUID id,
        UUID specimenId,
        UUID serviceRequestId
) {
}
