package com.ngockhanh.clinic.healthexamination.application.usecase;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportRowResponse;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportRowsPageResponse;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportType;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationImportJobRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ListParticipantImportRowsUseCase {
    private final HealthExaminationBatchRepository batches;
    private final HealthExaminationImportJobRepository jobs;

    public ParticipantImportRowsPageResponse execute(UUID organizationId, UUID batchId, UUID importId,
                                                     int page, int size, String status) {
        if (organizationId == null || batchId == null || importId == null || page < 1 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Import row pagination is invalid");
        }
        AggregateId batch = AggregateId.of(batchId);
        AggregateId organization = AggregateId.of(organizationId);
        AggregateId importJobId = AggregateId.of(importId);
        batches.findByIdAndOrganizationId(batch, organization)
                .orElseThrow(() -> new ResourceNotFoundException("Health examination batch"));
        var job = jobs.findSummaryByIdAndBatchId(importJobId, batch)
                .orElseThrow(() -> new ResourceNotFoundException("Participant import"));
        if (job.type() != ImportType.PARTICIPANT_LIST) {
            throw new ResourceNotFoundException("Participant import");
        }

        String rowFilter = rowFilter(status);
        long offset = (long) (page - 1) * size;
        long totalRows = jobs.countRowsByJobId(job.id(), rowFilter);
        List<ParticipantImportRowResponse> rows = jobs.findRowsByJobId(job.id(), rowFilter, offset, size).stream()
                .map(ParticipantImportRowResponse::from).toList();
        return new ParticipantImportRowsPageResponse(job.id().value(), page, size, totalRows, rows);
    }

    private static String rowFilter(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) return null;
        String normalized = status.strip().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "VALID", "INVALID", "WARNING", "CREATE", "UPDATE", "UNCHANGED" -> normalized;
            default -> throw new IllegalArgumentException("Import row filter is invalid");
        };
    }
}
