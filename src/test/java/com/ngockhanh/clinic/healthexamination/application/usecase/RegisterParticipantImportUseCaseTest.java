package com.ngockhanh.clinic.healthexamination.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.ngockhanh.clinic.healthexamination.application.port.out.ImportAttachmentMetadataRepository.ImportAttachmentMetadata;
import com.ngockhanh.clinic.healthexamination.application.port.out.ImportAttachmentMetadataRepository;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationImportJob;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportType;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository.HealthExaminationBatchReference;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationImportJobRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;

class RegisterParticipantImportUseCaseTest {
    @Test
    void savesSourceMetadataAndUploadJobTogetherAfterCheckingBatchOwnership() {
        UUID organizationId = id(1);
        UUID batchId = id(2);
        UUID jobId = id(3);
        UUID attachmentId = id(4);
        UUID actorId = id(5);
        HealthExaminationBatchRepository batches = mock(HealthExaminationBatchRepository.class);
        ImportAttachmentMetadataRepository attachments = mock(ImportAttachmentMetadataRepository.class);
        HealthExaminationImportJobRepository jobs = mock(HealthExaminationImportJobRepository.class);
        when(batches.findByIdAndOrganizationIdForUpdate(
                AggregateId.of(batchId), AggregateId.of(organizationId))).thenReturn(Optional.of(
                new HealthExaminationBatchReference(AggregateId.of(batchId), AggregateId.of(organizationId),
                        LocalDate.of(2026, 10, 1), BatchStatus.READY)));
        var attachment = new ImportAttachmentMetadata(attachmentId, jobId, actorId,
                "health-examination-imports/source.gcm", "roster.xls", "application/vnd.ms-excel",
                12, "hash", Instant.parse("2026-09-30T00:00:00Z"));
        var job = HealthExaminationImportJob.create(AggregateId.of(jobId), AggregateId.of(batchId),
                ImportType.PARTICIPANT_LIST, AggregateId.of(attachmentId), AggregateId.of(actorId),
                Instant.parse("2026-09-30T00:00:00Z"));
        RegisterParticipantImportUseCase useCase = new RegisterParticipantImportUseCase(batches, attachments, jobs);

        useCase.execute(organizationId, batchId, job, attachment);

        InOrder order = inOrder(attachments, jobs);
        order.verify(attachments).save(attachment);
        order.verify(jobs).save(job);
    }

    @Test
    void rejectsAJobForAnotherBatchBeforeSavingAnything() {
        HealthExaminationBatchRepository batches = mock(HealthExaminationBatchRepository.class);
        ImportAttachmentMetadataRepository attachments = mock(ImportAttachmentMetadataRepository.class);
        HealthExaminationImportJobRepository jobs = mock(HealthExaminationImportJobRepository.class);
        UUID organizationId = id(1);
        UUID batchId = id(2);
        when(batches.findByIdAndOrganizationIdForUpdate(
                AggregateId.of(batchId), AggregateId.of(organizationId))).thenReturn(Optional.of(
                new HealthExaminationBatchReference(AggregateId.of(batchId), AggregateId.of(organizationId),
                        LocalDate.of(2026, 10, 1), BatchStatus.READY)));
        var job = HealthExaminationImportJob.create(AggregateId.of(id(3)), AggregateId.of(id(9)), ImportType.PARTICIPANT_LIST);
        var attachment = new ImportAttachmentMetadata(id(4), id(3), id(5), "key", "roster.xls",
                "application/vnd.ms-excel", 10, "hash", Instant.now());
        RegisterParticipantImportUseCase useCase = new RegisterParticipantImportUseCase(batches, attachments, jobs);

        assertThatThrownBy(() -> useCase.execute(organizationId, batchId, job, attachment))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(attachments, jobs);
    }

    private static UUID id(long value) {
        return new UUID(0, value);
    }
}
