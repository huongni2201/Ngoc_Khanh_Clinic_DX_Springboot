package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record;

import java.util.UUID;

public record HealthExaminationImportRowRecord(
        UUID id,
        UUID healthExaminationImportJobId,
        Integer rowNumber,
        String participantCodeSnapshot,
        String identificationNumberSnapshot,
        String serviceCodeSnapshot,
        String validationStatus,
        String errorCodesJson,
        String normalizedPayloadJson,
        UUID resolvedPatientId,
        UUID resolvedHealthExaminationParticipantId,
        UUID resolvedBatchParticipantId,
        UUID resolvedBatchServiceId,
        UUID resolvedServiceRequestId
) {
}
