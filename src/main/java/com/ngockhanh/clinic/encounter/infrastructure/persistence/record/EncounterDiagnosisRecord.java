package com.ngockhanh.clinic.encounter.infrastructure.persistence.record;

import java.util.UUID;

import java.time.OffsetDateTime;

public record EncounterDiagnosisRecord(
        UUID id,
        UUID encounterId,
        UUID diagnosisCatalogId,
        String diagnosisText,
        String diagnosisType,
        Boolean isPrimary,
        UUID recordedByStaffId,
        OffsetDateTime recordedAt
) {
}
