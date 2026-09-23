package com.ngockhanh.clinic.diagnostics.infrastructure.persistence.record;

import java.util.UUID;

public record LabPanelItemRecord(
        UUID id,
        UUID labPanelId,
        UUID analyteId,
        Integer displayOrder
) {
}
