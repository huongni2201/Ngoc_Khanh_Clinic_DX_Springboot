package com.ngockhanh.clinic.prescription.infrastructure.persistence.record;

import java.time.LocalDateTime;
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
        LocalDateTime issuedAt,
        LocalDateTime createdAt
) {
}
