package com.ngockhanh.clinic.catalog.infrastructure.persistence.record;

import java.util.UUID;

public record MedicationRecord(
        UUID id,
        String medicationCode,
        String medicationName,
        String genericName,
        String strength,
        String dosageForm,
        String defaultRoute,
        String unit,
        Boolean isActive
) {
}
