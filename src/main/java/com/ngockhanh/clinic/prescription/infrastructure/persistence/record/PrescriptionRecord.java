package com.ngockhanh.clinic.prescription.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/** Persistence row of {@code public.prescriptions}. */
@Builder
public record PrescriptionRecord(UUID id, UUID encounterId, UUID createdBy, Instant createdAt) {}
