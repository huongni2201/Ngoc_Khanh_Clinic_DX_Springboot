package com.ngockhanh.clinic.healthexamination.domain.repository;

import java.util.Optional;
import java.util.List;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationImportJob;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationImportRow;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportType;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.ParticipantImportColumnMapping;

public interface HealthExaminationImportJobRepository {
    Optional<ImportJobSummary> findSummaryByIdAndBatchId(AggregateId importId, AggregateId batchId);
    Optional<HealthExaminationImportJob> findByIdAndBatchId(AggregateId importId, AggregateId batchId);
    Optional<HealthExaminationImportJob> findByIdAndBatchIdForUpdate(AggregateId importId, AggregateId batchId);
    long countRowsByJobId(AggregateId jobId, String rowFilter);
    List<HealthExaminationImportRow> findRowsByJobId(AggregateId jobId, String rowFilter, long offset, int limit);
    void save(HealthExaminationImportJob job);

    record ImportJobSummary(AggregateId id, ImportType type, ImportStatus status,
                            AggregateId sourceFileAttachmentId, ParticipantImportColumnMapping columnMapping,
                            int totalRows, int validRows, int warningRows, int errorRows) { }
}
