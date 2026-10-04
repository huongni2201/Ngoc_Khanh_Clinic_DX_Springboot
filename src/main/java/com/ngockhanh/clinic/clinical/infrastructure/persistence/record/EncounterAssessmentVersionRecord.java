package com.ngockhanh.clinic.clinical.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

/** Persistence row of {@code public.encounter_assessment_versions}. */
public record EncounterAssessmentVersionRecord(
    UUID id,
    UUID encounterAssessmentId,
    int versionNo,
    String status,
    UUID correctsVersionId,
    String correctionReason,
    String chiefComplaint,
    String historyOfPresentIllness,
    String pastMedicalHistory,
    String physicalExamination,
    String clinicalNote,
    UUID vitalSignId,
    UUID authoredBy,
    Instant createdAt,
    Instant updatedAt,
    UUID finalizedBy,
    Instant finalizedAt,
    long rowVersion) {}
