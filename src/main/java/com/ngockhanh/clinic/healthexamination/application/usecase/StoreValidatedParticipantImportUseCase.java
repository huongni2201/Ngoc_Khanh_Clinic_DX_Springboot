package com.ngockhanh.clinic.healthexamination.application.usecase;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportSummaryResponse;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationImportRow;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportType;
import com.ngockhanh.clinic.healthexamination.domain.enums.ParticipantImportField;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationImportJobRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.ParticipantImportColumnMapping;
import com.ngockhanh.clinic.shared.exception.BusinessRuleException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StoreValidatedParticipantImportUseCase {
    private final HealthExaminationBatchRepository batches;
    private final HealthExaminationImportJobRepository jobs;

    @PreAuthorize("hasAuthority('CLINIC_MANAGER')")
    @Transactional
    public ParticipantImportSummaryResponse execute(UUID organizationId, UUID batchId, UUID importId,
                                                     UUID actorUserId,
                                                     Map<ParticipantImportField, Integer> columns,
                                                     List<HealthExaminationImportRow> rows,
                                                     List<String> headers) {
        if (organizationId == null || batchId == null || importId == null || actorUserId == null
                || rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("Validated participant import details are required");
        }

        var batch = batches.findByIdAndOrganizationIdForUpdate(
                        AggregateId.of(batchId), AggregateId.of(organizationId))
                .orElseThrow(() -> new ResourceNotFoundException("Health examination batch"));
        if (!batch.status().allowsRosterImport()) {
            throw new BusinessRuleException("Roster import is not allowed for this batch state") { };
        }

        var job = jobs.findByIdAndBatchIdForUpdate(AggregateId.of(importId), AggregateId.of(batchId))
                .orElseThrow(() -> new ResourceNotFoundException("Participant import"));
        if (job.type() != ImportType.PARTICIPANT_LIST
                || (job.status() != ImportStatus.UPLOADED && job.status() != ImportStatus.VALIDATED)) {
            throw new BusinessRuleException("Participant import job is not editable") { };
        }

        ParticipantImportColumnMapping mapping = ParticipantImportColumnMapping.of(columns);
        if (job.status() == ImportStatus.UPLOADED) {
            job.mapColumns(mapping);
            rows.forEach(job::addRow);
            job.validate();
        } else {
            job.replaceValidatedRoster(mapping, rows);
        }
        jobs.save(job);
        return ParticipantImportSummaryResponse.from(job, headers);
    }
}
