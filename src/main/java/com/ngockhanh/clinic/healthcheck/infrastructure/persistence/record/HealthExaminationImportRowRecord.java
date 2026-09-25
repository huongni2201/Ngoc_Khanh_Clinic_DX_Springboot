package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record;

import java.util.UUID;

public record HealthExaminationImportRowRecord(
        UUID id,
        UUID healthExaminationImportJobId,
        Integer rowNumber,
        String employeeCodeSnapshot,
        String identificationNumberSnapshot,
        String serviceCodeSnapshot,
        String validationStatus,
        String errorCodesJson,
        String normalizedPayloadJson,
        UUID resolvedPatientId,
        UUID resolvedCompanyEmployeeId,
        UUID resolvedBatchEmployeeId,
        UUID resolvedBatchServiceId,
        UUID resolvedServiceRequestId
) {
}
