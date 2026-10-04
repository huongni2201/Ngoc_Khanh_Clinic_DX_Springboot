package com.ngockhanh.clinic.prescription.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

/** Persistence row of {@code public.prescriptions}. */
public record PrescriptionRecord(UUID id, UUID encounterId, UUID createdBy, Instant createdAt) {}
