package com.ngockhanh.clinic.diagnostics.infrastructure.persistence.record;

import java.util.UUID;
import lombok.Builder;

/** Persistence row of {@code public.lab_result_items}. */
@Builder
public record LabResultItemRecord(
    UUID id,
    UUID resultVersionId,
    UUID analyteId,
    String analyteCodeSnapshot,
    String analyteNameSnapshot,
    boolean requiredSnapshot,
    String valueType,
    String textValue,
    Boolean booleanValue,
    String unitSnapshot,
    String referenceRangeSnapshot,
    String abnormalFlag,
    String note) {}
