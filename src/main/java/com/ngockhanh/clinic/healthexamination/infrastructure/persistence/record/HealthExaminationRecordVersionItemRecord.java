package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

/** A row in public.health_examination_record_version_items. */
public record HealthExaminationRecordVersionItemRecord(
    UUID id,
    UUID recordVersionId,
    UUID serviceId,
    UUID participantServiceId,
    UUID serviceRequestId,
    UUID assessmentVersionId,
    UUID resultVersionId,
    UUID createdBy,
    Instant createdAt) {}
