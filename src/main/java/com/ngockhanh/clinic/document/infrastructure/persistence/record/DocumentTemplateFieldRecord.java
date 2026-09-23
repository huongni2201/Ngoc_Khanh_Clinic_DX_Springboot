package com.ngockhanh.clinic.document.infrastructure.persistence.record;

import java.util.UUID;

public record DocumentTemplateFieldRecord(
        UUID id,
        UUID documentTemplateVersionId,
        String fieldKey,
        String displayLabel,
        String itemType,
        String selectionMode,
        String eligibilityRuleJson,
        Integer displayOrder,
        Boolean isRequired
) {
}
