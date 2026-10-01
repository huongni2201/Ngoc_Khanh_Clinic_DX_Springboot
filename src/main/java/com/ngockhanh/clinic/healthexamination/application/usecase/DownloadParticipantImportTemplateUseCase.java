package com.ngockhanh.clinic.healthexamination.application.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.infrastructure.spreadsheet.FesodParticipantTemplateWriter;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DownloadParticipantImportTemplateUseCase {
    private final HealthExaminationBatchRepository batches;
    private final FesodParticipantTemplateWriter templateWriter;

    public byte[] execute(UUID organizationId, UUID batchId) {
        if (organizationId == null || batchId == null) {
            throw new IllegalArgumentException("Organization and batch identifiers are required");
        }
        batches.findByIdAndOrganizationId(
                        AggregateId.of(batchId), AggregateId.of(organizationId))
                .orElseThrow(() -> new ResourceNotFoundException("Health examination batch"));
        return templateWriter.generate();
    }
}
