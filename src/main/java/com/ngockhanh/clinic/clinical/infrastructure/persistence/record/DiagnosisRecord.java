package com.ngockhanh.clinic.clinical.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/** Persistence row of {@code public.diagnoses}. */
@Builder
public record DiagnosisRecord(
    UUID id,
    UUID assessmentVersionId,
    String diagnosisCode,
    String diagnosisName,
    String diagnosisType,
    String note,
    UUID recordedBy,
    Instant recordedAt) {}
