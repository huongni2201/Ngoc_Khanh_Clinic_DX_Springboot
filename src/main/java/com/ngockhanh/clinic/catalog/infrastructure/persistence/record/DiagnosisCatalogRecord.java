package com.ngockhanh.clinic.catalog.infrastructure.persistence.record;

import java.util.UUID;

public record DiagnosisCatalogRecord(
        UUID id,
        String diagnosisCode,
        String nameVi,
        String nameEn,
        String parentCode,
        Boolean isActive
) {
}
