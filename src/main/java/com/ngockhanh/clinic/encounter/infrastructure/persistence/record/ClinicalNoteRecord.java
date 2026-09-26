package com.ngockhanh.clinic.encounter.infrastructure.persistence.record;

import java.util.UUID;

import java.time.Instant;

public record ClinicalNoteRecord(
        UUID id,
        UUID encounterId,
        String noteType,
        String contentJson,
        UUID authorStaffId,
        String status,
        Instant createdAt,
        Instant finalizedAt
) {
}
