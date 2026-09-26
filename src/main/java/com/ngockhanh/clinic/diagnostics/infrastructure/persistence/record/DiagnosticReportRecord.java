package com.ngockhanh.clinic.diagnostics.infrastructure.persistence.record;

import java.time.OffsetDateTime;
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
        OffsetDateTime finalizedAt,
        OffsetDateTime createdAt,
        OffsetDateTime releasedToPatientAt,
        UUID releasedToPatientByUserId
) {
}
