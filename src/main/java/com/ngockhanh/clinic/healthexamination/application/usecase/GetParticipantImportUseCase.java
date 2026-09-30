package com.ngockhanh.clinic.healthexamination.application.usecase;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.ngockhanh.clinic.healthexamination.application.port.out.ImportAttachmentMetadataRepository.ImportAttachmentMetadata;
import com.ngockhanh.clinic.healthexamination.application.port.out.ImportAttachmentMetadataRepository;
import com.ngockhanh.clinic.healthexamination.application.port.out.ImportFileStorage;
import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantSpreadsheetReader;
import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantSpreadsheetReader.SpreadsheetFormat;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportSummaryResponse;
import com.ngockhanh.clinic.healthexamination.application.validation.ParticipantRosterHeaderMapper;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationImportJob;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportType;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationImportJobRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetParticipantImportUseCase {
    private final HealthExaminationBatchRepository batches;
    private final HealthExaminationImportJobRepository jobs;
    private final ImportAttachmentMetadataRepository attachments;
    private final ImportFileStorage storage;
    private final ParticipantSpreadsheetReader spreadsheets;
    private final ParticipantRosterHeaderMapper headerMapper;

    @PreAuthorize("hasAuthority('CLINIC_MANAGER')")
    public ParticipantImportSummaryResponse execute(UUID organizationId, UUID batchId, UUID importId) {
        if (organizationId == null || batchId == null || importId == null) {
            throw new IllegalArgumentException("Participant import identifiers are required");
        }
        AggregateId batch = AggregateId.of(batchId);
        AggregateId organization = AggregateId.of(organizationId);
        AggregateId importJobId = AggregateId.of(importId);
        batches.findByIdAndOrganizationId(batch, organization)
                .orElseThrow(() -> new ResourceNotFoundException("Health examination batch"));
        HealthExaminationImportJob job = jobs.findByIdAndBatchId(importJobId, batch)
                .orElseThrow(() -> new ResourceNotFoundException("Participant import"));
        if (job.type() != ImportType.PARTICIPANT_LIST || job.sourceFileAttachmentId() == null) {
            throw new ResourceNotFoundException("Participant import");
        }
        ImportAttachmentMetadata attachment = attachments.findById(job.sourceFileAttachmentId().value())
                .orElseThrow(() -> new ResourceNotFoundException("Participant import source file"));
        ParticipantSpreadsheetReader.SpreadsheetHeader header = readHeader(attachment);
        return ParticipantImportSummaryResponse.from(job, header.values(), headerMapper.suggest(header.values()));
    }

    private ParticipantSpreadsheetReader.SpreadsheetHeader readHeader(ImportAttachmentMetadata attachment) {
        try (InputStream input = storage.open(attachment.storageKey())) {
            return spreadsheets.readHeader(input, format(attachment.fileName()));
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to read participant import source", failure);
        }
    }

    private static SpreadsheetFormat format(String fileName) {
        String lower = fileName.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".xlsx")) return SpreadsheetFormat.XLSX;
        if (lower.endsWith(".xls")) return SpreadsheetFormat.XLS;
        throw new IllegalArgumentException("Unsupported stored spreadsheet format");
    }
}
