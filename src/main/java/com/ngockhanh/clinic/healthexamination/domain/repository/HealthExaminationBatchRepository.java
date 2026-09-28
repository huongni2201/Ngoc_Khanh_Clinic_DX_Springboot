package com.ngockhanh.clinic.healthexamination.domain.repository;

import java.util.Optional;

import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;

public interface HealthExaminationBatchRepository {
    Optional<HealthExaminationBatchReference> findById(AggregateId id);
    record HealthExaminationBatchReference(AggregateId id, AggregateId organizationId,
                                          java.time.LocalDate startDate) {}
}
