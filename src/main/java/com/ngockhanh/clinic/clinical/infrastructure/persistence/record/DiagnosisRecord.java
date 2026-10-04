package com.ngockhanh.clinic.clinical.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

/** Persistence row of {@code public.diagnoses}. */
public record DiagnosisRecord(
    UUID id,
    UUID assessmentVersionId,
    String diagnosisCode,
    String diagnosisName,
    String diagnosisType,
    String note,
    UUID recordedBy,
    Instant recordedAt) {}
