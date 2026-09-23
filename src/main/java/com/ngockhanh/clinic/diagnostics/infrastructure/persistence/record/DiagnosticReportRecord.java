package com.ngockhanh.clinic.diagnostics.infrastructure.persistence.record;

import java.time.LocalDateTime;
import java.util.UUID;

public record DiagnosticReportRecord(
        UUID id,
        UUID serviceRequestId,
        UUID imagingStudyId,
        UUID documentTemplateVersionId,
        Integer versionNumber,
        String status,
        String findings,
        String conclusion,
        String structuredDataJson,
        UUID supersedesReportId,
        UUID authorStaffId,
        UUID verifiedByStaffId,
        LocalDateTime finalizedAt,
        LocalDateTime createdAt,
        LocalDateTime releasedToPatientAt,
        UUID releasedToPatientByUserId
) {
}
