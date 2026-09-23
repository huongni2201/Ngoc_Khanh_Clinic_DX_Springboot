package com.ngockhanh.clinic.diagnostics.infrastructure.persistence.record;

import java.util.UUID;

public record LabPanelRecord(
        UUID id,
        String panelCode,
        String panelName,
        Boolean isActive
) {
}
