package com.ngockhanh.clinic.healthexamination.application.usecase;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportRowResponse;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportRowsPageResponse;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationImportJob;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationImportRow;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportRowAction;
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

    @PreAuthorize("hasAuthority('CLINIC_MANAGER')")
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
        HealthExaminationImportJob job = jobs.findByIdAndBatchId(importJobId, batch)
                .orElseThrow(() -> new ResourceNotFoundException("Participant import"));
        if (job.type() != ImportType.PARTICIPANT_LIST) {
            throw new ResourceNotFoundException("Participant import");
        }

        Predicate<HealthExaminationImportRow> filter = filter(status);
        List<HealthExaminationImportRow> matched = job.rows().stream().filter(filter).toList();
        long offset = (long) (page - 1) * size;
        List<ParticipantImportRowResponse> rows = offset >= matched.size() ? List.of()
                : matched.subList((int) offset, Math.min((int) offset + size, matched.size())).stream()
                        .map(ParticipantImportRowResponse::from).toList();
        return new ParticipantImportRowsPageResponse(job.id().value(), page, size, matched.size(), rows);
    }

    private static Predicate<HealthExaminationImportRow> filter(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) return ignored -> true;
        return switch (status.strip().toUpperCase(Locale.ROOT)) {
            case "VALID" -> HealthExaminationImportRow::isValid;
            case "INVALID" -> row -> !row.isValid();
            case "WARNING" -> row -> !row.getWarningCodes().isEmpty();
            case "CREATE" -> row -> row.getAppliedAction() == ImportRowAction.CREATE;
            case "UPDATE" -> row -> row.getAppliedAction() == ImportRowAction.UPDATE;
            case "UNCHANGED" -> row -> row.getAppliedAction() == ImportRowAction.UNCHANGED;
            default -> throw new IllegalArgumentException("Import row filter is invalid");
        };
    }
}
