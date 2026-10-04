package com.ngockhanh.clinic.clinical.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

/** Persistence row of {@code public.encounter_assessments}. */
public record EncounterAssessmentRecord(
    UUID id, UUID encounterId, UUID createdBy, Instant createdAt, long rowVersion) {}
