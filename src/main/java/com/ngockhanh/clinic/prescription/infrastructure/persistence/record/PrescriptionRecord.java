package com.ngockhanh.clinic.prescription.infrastructure.persistence.record;

import java.time.Instant;
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
        Instant issuedAt,
        Instant createdAt
) {
}
