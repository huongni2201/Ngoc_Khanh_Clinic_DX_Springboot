package com.ngockhanh.clinic.healthexamination.application.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ngockhanh.clinic.healthexamination.application.port.out.ImportAttachmentMetadataRepository.ImportAttachmentMetadata;
import com.ngockhanh.clinic.healthexamination.application.port.out.ImportAttachmentMetadataRepository;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationImportJob;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportType;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationImportJobRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.BusinessRuleException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RegisterParticipantImportUseCase {
    private final HealthExaminationBatchRepository batches;
    private final ImportAttachmentMetadataRepository attachments;
    private final HealthExaminationImportJobRepository jobs;

    @Transactional
    public void execute(UUID organizationId, UUID batchId,
                        HealthExaminationImportJob job, ImportAttachmentMetadata attachment) {
        if (organizationId == null || batchId == null || job == null || attachment == null) {
            throw new IllegalArgumentException("Participant import registration details are required");
        }
        if (!job.id().value().equals(attachment.importJobId())
                || job.sourceFileAttachmentId() == null
                || !job.sourceFileAttachmentId().value().equals(attachment.id())
                || !job.batchId().equals(AggregateId.of(batchId))
                || job.type() != ImportType.PARTICIPANT_LIST
                || job.status() != ImportStatus.UPLOADED) {
            throw new IllegalArgumentException("Participant import registration is inconsistent");
        }

        var batch = batches.findByIdAndOrganizationIdForUpdate(
                        AggregateId.of(batchId), AggregateId.of(organizationId))
                .orElseThrow(() -> new ResourceNotFoundException("Health examination batch"));
        if (!batch.status().allowsRosterImport()) {
            throw new BusinessRuleException("Roster import is not allowed for this batch state") { };
        }

        attachments.save(attachment);
        jobs.save(job);
    }

}
