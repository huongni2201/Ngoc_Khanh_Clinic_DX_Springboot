package com.ngockhanh.clinic.prescription.infrastructure.persistence.record;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PrescriptionRecord(
        UUID id,
        String prescriptionNumber,
        UUID encounterId,
        UUID patientId,
        UUID prescriberStaffId,
        Integer versionNumber,
        String status,
        UUID supersedesPrescriptionId,
        OffsetDateTime issuedAt,
        OffsetDateTime createdAt
) {
}
