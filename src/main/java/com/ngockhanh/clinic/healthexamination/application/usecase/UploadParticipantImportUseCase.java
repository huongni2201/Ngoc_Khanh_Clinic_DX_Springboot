package com.ngockhanh.clinic.healthexamination.application.usecase;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.ngockhanh.clinic.healthexamination.application.command.UploadParticipantImportCommand;
import com.ngockhanh.clinic.healthexamination.application.port.out.ImportAttachmentMetadataRepository.ImportAttachmentMetadata;
import com.ngockhanh.clinic.healthexamination.application.port.out.ImportFileStorage;
import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantSpreadsheetReader;
import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantSpreadsheetReader.SpreadsheetFormat;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportUploadResponse;
import com.ngockhanh.clinic.healthexamination.application.validation.ParticipantRosterHeaderMapper;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationImportJob;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportType;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository.HealthExaminationBatchReference;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.BusinessRuleException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UploadParticipantImportUseCase {
    private static final byte[] XLS_MAGIC = {
            (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
    private static final byte[] XLSX_MAGIC = {'P', 'K', 3, 4};
    private static final long DEFAULT_MAX_FILE_BYTES = 10L * 1024 * 1024;

    private final HealthExaminationBatchRepository batches;
    private final ImportFileStorage storage;
    private final ParticipantSpreadsheetReader spreadsheets;
    private final ParticipantRosterHeaderMapper headerMapper;
    private final RegisterParticipantImportUseCase registerImport;

    @Value("${clinic.health-examination.employee-import.max-file-size-bytes:10485760}")
    private long maxFileSizeBytes = DEFAULT_MAX_FILE_BYTES;

    @PreAuthorize("hasAuthority('CLINIC_MANAGER')")
    public ParticipantImportUploadResponse execute(UploadParticipantImportCommand command) {
        if (command == null || command.organizationId() == null || command.batchId() == null
                || command.actorUserId() == null || command.content() == null) {
            throw new IllegalArgumentException("Import upload details are required");
        }
        if (command.sizeBytes() < 1 || command.sizeBytes() > maxFileSizeBytes) {
            throw new IllegalArgumentException("Import file size is outside the allowed limit");
        }

        AggregateId organizationId = AggregateId.of(command.organizationId());
        AggregateId batchId = AggregateId.of(command.batchId());
        HealthExaminationBatchReference batch = batches.findByIdAndOrganizationId(batchId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Health examination batch"));
        requireRosterEditable(batch.status());

        UUID jobId = UuidV7Generator.generate();
        UUID attachmentId = UuidV7Generator.generate();
        String fileName = safeFileName(command.fileName());
        SpreadsheetFormat format;
        PushbackInputStream input = new PushbackInputStream(command.content(), 8);
        try {
            format = detectFormat(fileName, input);
        } catch (IOException failure) {
            throw new IllegalArgumentException("Invalid spreadsheet file", failure);
        }

        ImportFileStorage.StoredFile stored;
        try {
            stored = storage.store(jobId, input, maxFileSizeBytes);
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to store encrypted import file", failure);
        }

        try {
            ParticipantSpreadsheetReader.SpreadsheetHeader header;
            try (InputStream source = storage.open(stored.storageKey())) {
                header = spreadsheets.readHeader(source, format);
            }
            var suggestedMapping = headerMapper.suggest(header.values());
            Instant createdAt = Instant.now();
            AggregateId jobAggregateId = new AggregateId(jobId);
            HealthExaminationImportJob job = HealthExaminationImportJob.create(jobAggregateId, batchId,
                    ImportType.PARTICIPANT_LIST, new AggregateId(attachmentId),
                    new AggregateId(command.actorUserId()), createdAt);

            ImportAttachmentMetadata attachment = new ImportAttachmentMetadata(attachmentId, jobId,
                    command.actorUserId(), stored.storageKey(), fileName, mimeType(format),
                    stored.sizeBytes(), stored.sha256(), createdAt);
            registerImport.execute(command.organizationId(), command.batchId(), job, attachment);
            log.info("Participant roster file uploaded: importId={}, batchId={}, sizeBytes={}",
                    jobId, command.batchId(), stored.sizeBytes());
            return new ParticipantImportUploadResponse(jobId, job.status().name(), header.rowNumber(),
                    header.values(), suggestedMapping);
        } catch (RuntimeException failure) {
            deleteAfterFailure(stored.storageKey(), jobId);
            throw failure;
        } catch (IOException failure) {
            deleteAfterFailure(stored.storageKey(), jobId);
            throw new IllegalStateException("Unable to read encrypted import file", failure);
        }
    }

    private static void requireRosterEditable(BatchStatus status) {
        if (status != BatchStatus.DRAFT && status != BatchStatus.READY && status != BatchStatus.IN_PROGRESS) {
            throw new BusinessRuleException("Roster import is not allowed for this batch state") { };
        }
    }

    private static SpreadsheetFormat detectFormat(String fileName, PushbackInputStream input) throws IOException {
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        byte[] prefix = input.readNBytes(8);
        input.unread(prefix);
        boolean xls = startsWith(prefix, XLS_MAGIC);
        boolean xlsx = startsWith(prefix, XLSX_MAGIC);
        if (lowerName.endsWith(".xls") && xls) return SpreadsheetFormat.XLS;
        if (lowerName.endsWith(".xlsx") && xlsx) return SpreadsheetFormat.XLSX;
        throw new IllegalArgumentException("File content does not match a supported Excel format");
    }

    private static boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) {
            if (value[index] != prefix[index]) return false;
        }
        return true;
    }

    private static String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("Import file name is required");
        String normalized = fileName.replace('\\', '/');
        String baseName = normalized.substring(normalized.lastIndexOf('/') + 1).strip();
        if (baseName.length() > 255) baseName = baseName.substring(baseName.length() - 255);
        return baseName;
    }

    private static String mimeType(SpreadsheetFormat format) {
        return format == SpreadsheetFormat.XLS
                ? "application/vnd.ms-excel"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

    private void deleteAfterFailure(String storageKey, UUID jobId) {
        try {
            storage.delete(storageKey);
        } catch (IOException cleanupFailure) {
            log.warn("Encrypted import source cleanup failed: importId={}", jobId);
        }
    }
}
