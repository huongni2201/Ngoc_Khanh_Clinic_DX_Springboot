package com.ngockhanh.clinic.healthexamination.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantImportAuditWriter;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationImportJob;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportType;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository.HealthExaminationBatchReference;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationImportJobRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;

class CancelParticipantImportUseCaseTest {
    @Test
    void rejectsABatchOutsideTheOrganizationBeforeLoadingTheImport() {
        UUID organizationId = id(1);
        UUID batchId = id(2);
        UUID importId = id(3);
        UUID actorId = id(4);
        var batches = mock(HealthExaminationBatchRepository.class);
        var jobs = mock(HealthExaminationImportJobRepository.class);
        var audit = mock(ParticipantImportAuditWriter.class);
        when(batches.findByIdAndOrganizationId(AggregateId.of(batchId), AggregateId.of(organizationId)))
                .thenReturn(Optional.empty());
        CancelParticipantImportUseCase useCase = new CancelParticipantImportUseCase(batches, jobs, audit);

        assertThatThrownBy(() -> useCase.execute(organizationId, batchId, importId, actorId))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(jobs, audit);
    }

    @Test
    void cancelsOnceAndWritesAnAuditRecord() {
        UUID organizationId = id(1);
        UUID batchId = id(2);
        UUID importId = id(3);
        UUID actorId = id(4);
        var batches = mock(HealthExaminationBatchRepository.class);
        var jobs = mock(HealthExaminationImportJobRepository.class);
        var audit = mock(ParticipantImportAuditWriter.class);
        HealthExaminationImportJob job = HealthExaminationImportJob.create(AggregateId.of(importId),
                AggregateId.of(batchId), ImportType.PARTICIPANT_LIST,
                AggregateId.of(id(5)), AggregateId.of(actorId), Instant.parse("2026-09-30T00:00:00Z"));
        when(batches.findByIdAndOrganizationId(AggregateId.of(batchId), AggregateId.of(organizationId)))
                .thenReturn(Optional.of(
                new HealthExaminationBatchReference(AggregateId.of(batchId), AggregateId.of(organizationId),
                        LocalDate.of(2026, 10, 1), BatchStatus.READY)));
        when(jobs.findByIdAndBatchIdForUpdate(AggregateId.of(importId), AggregateId.of(batchId)))
                .thenAnswer(ignored -> Optional.of(job));
        CancelParticipantImportUseCase useCase = new CancelParticipantImportUseCase(batches, jobs, audit);

        var first = useCase.execute(organizationId, batchId, importId, actorId);
        var retry = useCase.execute(organizationId, batchId, importId, actorId);

        assertThat(first.status()).isEqualTo("CANCELED");
        assertThat(retry.status()).isEqualTo("CANCELED");
        verify(batches, org.mockito.Mockito.times(2))
                .findByIdAndOrganizationId(AggregateId.of(batchId), AggregateId.of(organizationId));
        verify(batches, never()).findByIdAndOrganizationIdForUpdate(
                AggregateId.of(batchId), AggregateId.of(organizationId));
        verify(jobs, org.mockito.Mockito.times(2))
                .findByIdAndBatchIdForUpdate(AggregateId.of(importId), AggregateId.of(batchId));
        verify(jobs).save(job);
        verify(audit).record(org.mockito.ArgumentMatchers.any());
        verify(audit).record(org.mockito.ArgumentMatchers.argThat(entry ->
                entry.actorUserId().equals(actorId) && entry.importJobId().equals(importId)
                        && entry.action().equals("PARTICIPANT_ROSTER_IMPORT_CANCELED")));
        verify(jobs, never()).save(org.mockito.ArgumentMatchers.argThat(saved -> saved.status().name().equals("CONFIRMED")));
    }

    private static UUID id(long value) {
        return new UUID(0L, value);
    }
}
