package com.ngockhanh.clinic.healthcheck.application.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ngockhanh.clinic.healthcheck.application.importparticipant.ParticipantImportPreview;
import com.ngockhanh.clinic.healthcheck.application.port.ParticipantImportBatchQuery;
import com.ngockhanh.clinic.healthcheck.domain.repository.HealthExaminationImportJobRepository;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;

@Service
public class GetParticipantImportPreviewUseCase {
    private final ParticipantImportBatchQuery batchQuery;
    private final HealthExaminationImportJobRepository importJobRepository;

    public GetParticipantImportPreviewUseCase(ParticipantImportBatchQuery batchQuery,
                                               HealthExaminationImportJobRepository importJobRepository) {
        this.batchQuery = batchQuery;
        this.importJobRepository = importJobRepository;
    }

    @Transactional(readOnly = true)
    public ParticipantImportPreview execute(UUID organizationId, UUID batchId, UUID importId) {
        batchQuery.findById(batchId).filter(batch -> batch.organizationId().equals(organizationId))
                .orElseThrow(() -> new ResourceNotFoundException("Health examination batch"));
        return importJobRepository.findById(importId).filter(job -> job.batchId().equals(batchId))
                .map(StageParticipantImportUseCase::toPreview)
                .orElseThrow(() -> new ResourceNotFoundException("Participant import"));
    }
}
