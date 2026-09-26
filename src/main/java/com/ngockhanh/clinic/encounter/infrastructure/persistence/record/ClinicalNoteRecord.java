package com.ngockhanh.clinic.encounter.infrastructure.persistence.record;

import java.util.UUID;

import java.time.OffsetDateTime;

public record ClinicalNoteRecord(
        UUID id,
        UUID encounterId,
        String noteType,
        String contentJson,
        UUID authorStaffId,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime finalizedAt
) {
}
