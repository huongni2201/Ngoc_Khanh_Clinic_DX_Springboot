package com.ngockhanh.clinic.encounter.infrastructure.persistence.record;

import java.util.UUID;

import java.time.LocalDateTime;

public record ClinicalNoteRecord(
        UUID id,
        UUID encounterId,
        String noteType,
        String contentJson,
        UUID authorStaffId,
        String status,
        LocalDateTime createdAt,
        LocalDateTime finalizedAt
) {
}
