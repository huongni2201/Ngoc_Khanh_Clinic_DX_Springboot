package com.ngockhanh.clinic.diagnostics.infrastructure.persistence.record;

import java.util.UUID;

public record AnalyteRecord(
        UUID id,
        String analyteCode,
        String analyteName,
        String defaultUnit,
        String valueType,
        Boolean isActive
) {
}
