package com.ngockhanh.clinic.prescription.infrastructure.persistence.record;

import java.math.BigDecimal;
import java.util.UUID;

/** Persistence row of {@code public.prescription_items}. */
public record PrescriptionItemRecord(
    UUID id,
    UUID prescriptionVersionId,
    UUID medicineId,
    String medicineNameSnapshot,
    String strengthSnapshot,
    String dose,
    String route,
    String frequency,
    String duration,
    BigDecimal quantity,
    String instruction) {}
