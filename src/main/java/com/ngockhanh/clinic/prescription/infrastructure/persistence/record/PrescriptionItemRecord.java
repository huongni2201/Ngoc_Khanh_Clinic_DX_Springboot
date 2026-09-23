package com.ngockhanh.clinic.prescription.infrastructure.persistence.record;

import java.util.UUID;

import java.math.BigDecimal;

public record PrescriptionItemRecord(
        UUID id,
        UUID prescriptionId,
        UUID medicationId,
        String medicationNameSnapshot,
        String strengthSnapshot,
        String dose,
        String route,
        String frequency,
        Integer durationDays,
        BigDecimal quantity,
        String instructions,
        Integer displayOrder
) {
}
