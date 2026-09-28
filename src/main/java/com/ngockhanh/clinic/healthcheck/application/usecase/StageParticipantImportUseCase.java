package com.ngockhanh.clinic.healthcheck.application.usecase;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ngockhanh.clinic.healthcheck.application.importparticipant.ImportValidationError;
import com.ngockhanh.clinic.healthcheck.application.importparticipant.ParticipantImportBatchContext;
import com.ngockhanh.clinic.healthcheck.application.importparticipant.ParticipantImportPreview;
import com.ngockhanh.clinic.healthcheck.application.importparticipant.ParticipantImportUpload;
import com.ngockhanh.clinic.healthcheck.application.importparticipant.ParticipantImportValidator;
import com.ngockhanh.clinic.healthcheck.application.importparticipant.ParticipantImportWorkbookException;
import com.ngockhanh.clinic.healthcheck.application.importparticipant.RawParticipantImportRow;
import com.ngockhanh.clinic.healthcheck.application.importparticipant.ValidatedParticipantImportRow;
import com.ngockhanh.clinic.healthcheck.application.port.ImportAttachmentMetadata;
import com.ngockhanh.clinic.healthcheck.application.port.ImportAttachmentMetadataRepository;
import com.ngockhanh.clinic.healthcheck.application.port.ParticipantImportBatchQuery;
import com.ngockhanh.clinic.healthcheck.application.port.ParticipantImportSourceStorage;
import com.ngockhanh.clinic.healthcheck.application.port.ParticipantRosterWorkbookReader;
import com.ngockhanh.clinic.healthcheck.domain.aggregate.HealthExaminationImportJob;
import com.ngockhanh.clinic.healthcheck.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthcheck.domain.entity.HealthExaminationImportRow;
import com.ngockhanh.clinic.healthcheck.domain.enums.ImportType;
import com.ngockhanh.clinic.healthcheck.domain.repository.HealthExaminationBatchParticipantRepository;
import com.ngockhanh.clinic.healthcheck.domain.repository.HealthExaminationImportJobRepository;
import com.ngockhanh.clinic.healthcheck.domain.repository.HealthExaminationParticipantRepository;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import com.ngockhanh.clinic.shared.infrastructure.id.IdGenerator;

@Service
public class StageParticipantImportUseCase {
    private static final int MAX_FILE_BYTES = 20 * 1024 * 1024;
    private static final String XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ParticipantImportBatchQuery batchQuery;
    private final ParticipantRosterWorkbookReader workbookReader;
    private final ParticipantImportValidator validator;
    private final HealthExaminationParticipantRepository participantRepository;
    private final HealthExaminationBatchParticipantRepository batchParticipantRepository;
    private final HealthExaminationImportJobRepository importJobRepository;
    private final ImportAttachmentMetadataRepository attachmentRepository;
    private final ParticipantImportSourceStorage sourceStorage;
    private final IdGenerator idGenerator;

    public StageParticipantImportUseCase(ParticipantImportBatchQuery batchQuery,
                                         ParticipantRosterWorkbookReader workbookReader,
                                         ParticipantImportValidator validator,
                                         HealthExaminationParticipantRepository participantRepository,
                                         HealthExaminationBatchParticipantRepository batchParticipantRepository,
                                         HealthExaminationImportJobRepository importJobRepository,
                                         ImportAttachmentMetadataRepository attachmentRepository,
                                         ParticipantImportSourceStorage sourceStorage,
                                         IdGenerator idGenerator) {
        this.batchQuery = batchQuery;
        this.workbookReader = workbookReader;
        this.validator = validator;
        this.participantRepository = participantRepository;
        this.batchParticipantRepository = batchParticipantRepository;
        this.importJobRepository = importJobRepository;
        this.attachmentRepository = attachmentRepository;
        this.sourceStorage = sourceStorage;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public ParticipantImportPreview execute(ParticipantImportUpload upload) {
        validateUpload(upload);
        ParticipantImportBatchContext batch = batchQuery.findById(upload.batchId())
                .filter(found -> found.organizationId().equals(upload.organizationId()))
                .orElseThrow(() -> new ResourceNotFoundException("Health examination batch"));
        if (batch.startDate() == null) throw new IllegalArgumentException("Batch examination date is required");

        var parsed = workbookReader.read(upload.content());
        List<ValidatedParticipantImportRow> validated = validator.validate(parsed.rows(), batch.startDate());
        List<HealthExaminationParticipant> matchesByCode = participantRepository.findByOrganizationAndCodes(
                batch.organizationId(), validated.stream().filter(ValidatedParticipantImportRow::valid)
                        .map(ValidatedParticipantImportRow::participantCode).toList());
        List<IdentificationNumber> identificationNumbers = validated.stream().filter(ValidatedParticipantImportRow::valid)
                .map(row -> row.snapshot().identificationNumber()).toList();
        List<HealthExaminationParticipant> matchesByIdentification =
                participantRepository.findByOrganizationAndIdentificationNumbers(batch.organizationId(), identificationNumbers);
        Map<String, HealthExaminationParticipant> byCode = new HashMap<>();
        matchesByCode.forEach(participant -> byCode.put(participant.participantCode(), participant));
        Map<String, HealthExaminationParticipant> byIdentification = new HashMap<>();
        matchesByIdentification.forEach(participant -> byIdentification.put(
                participant.identificationNumber().value(), participant));
        Set<UUID> matchedIds = new HashSet<>();
        matchesByCode.forEach(participant -> matchedIds.add(participant.id()));
        matchesByIdentification.forEach(participant -> matchedIds.add(participant.id()));
        Set<UUID> alreadyInBatch = batchParticipantRepository.findParticipantIdsByBatch(batch.id(), matchedIds);

        UUID jobId = idGenerator.next();
        UUID attachmentId = idGenerator.next();
        Instant now = Instant.now();
        String storageKey;
        try {
            storageKey = sourceStorage.store(attachmentId, upload.content());
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to store encrypted import source", failure);
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        try {
                            sourceStorage.delete(storageKey);
                        } catch (IOException cleanupFailure) {
                            throw new IllegalStateException("Unable to remove rolled back import source", cleanupFailure);
                        }
                    }
                }
            });
        }

        String fileName = safeFileName(upload.fileName());
        String mimeType = upload.mimeType() == null || upload.mimeType().isBlank() ? XLSX_MIME : upload.mimeType();
        attachmentRepository.save(new ImportAttachmentMetadata(attachmentId, jobId, upload.actorUserId(), storageKey,
                fileName, mimeType, upload.content().length, sha256(upload.content()), now));

        HealthExaminationImportJob job = HealthExaminationImportJob.create(jobId, batch.id(), ImportType.PARTICIPANT_LIST,
                attachmentId, upload.actorUserId(), now);
        for (ValidatedParticipantImportRow row : validated) {
            List<String> errors = new ArrayList<>(row.errors().stream().map(ImportValidationError::code).toList());
            if (row.valid()) addDatabaseErrors(row, byCode, byIdentification, alreadyInBatch, errors);
            HealthExaminationImportRow importRow = errors.isEmpty()
                    ? HealthExaminationImportRow.roster(idGenerator.next(), row.rowNumber(), row.participantCode(),
                            row.snapshot(), row.departmentName(), row.jobTitle(), row.occupation())
                    : HealthExaminationImportRow.invalid(idGenerator.next(), row.rowNumber(), errors,
                            row.participantCode(), row.snapshot(), row.departmentName(), row.jobTitle(), row.occupation());
            job.addRow(importRow);
        }
        job.validate();
        importJobRepository.save(job);
        return toPreview(job);
    }

    private static void addDatabaseErrors(ValidatedParticipantImportRow row,
                                          Map<String, HealthExaminationParticipant> byCode,
                                          Map<String, HealthExaminationParticipant> byIdentification,
                                          Set<UUID> alreadyInBatch, List<String> errors) {
        HealthExaminationParticipant codeMatch = byCode.get(row.participantCode());
        HealthExaminationParticipant identificationMatch = byIdentification.get(
                row.snapshot().identificationNumber().value());
        if (codeMatch != null && identificationMatch != null && !codeMatch.id().equals(identificationMatch.id())) {
            errors.add("PARTICIPANT_IDENTITY_CONFLICT");
            return;
        }
        HealthExaminationParticipant existing = identificationMatch != null ? identificationMatch : codeMatch;
        if (existing == null) return;
        if (codeMatch != null && !codeMatch.id().equals(existing.id())) errors.add("PARTICIPANT_CODE_CONFLICT");
        if (existing.patientId() != null && !existing.participantCode().equals(row.participantCode())) {
            errors.add("LINKED_PARTICIPANT_IDENTITY_REVIEW");
        }
        if (alreadyInBatch.contains(existing.id())) errors.add("DUPLICATED_IN_BATCH");
    }

    private static void validateUpload(ParticipantImportUpload upload) {
        if (upload == null || upload.organizationId() == null || upload.batchId() == null || upload.actorUserId() == null
                || upload.content() == null || upload.content().length == 0) {
            throw new IllegalArgumentException("Invalid participant import upload");
        }
        if (upload.content().length > MAX_FILE_BYTES) throw new ParticipantImportWorkbookException("FILE_TOO_LARGE");
        if (upload.fileName() == null || !upload.fileName().toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")) {
            throw new ParticipantImportWorkbookException("UNSUPPORTED_FILE_TYPE");
        }
        if (upload.mimeType() != null && !upload.mimeType().isBlank()
                && !XLSX_MIME.equalsIgnoreCase(upload.mimeType())
                && !"application/octet-stream".equalsIgnoreCase(upload.mimeType())) {
            throw new ParticipantImportWorkbookException("UNSUPPORTED_FILE_TYPE");
        }
    }

    private static String safeFileName(String fileName) {
        String name = fileName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).strip();
        if (name.isBlank() || name.length() > 255) throw new ParticipantImportWorkbookException("INVALID_FILE_NAME");
        return name;
    }

    private static String sha256(byte[] content) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    static ParticipantImportPreview toPreview(HealthExaminationImportJob job) {
        List<ParticipantImportPreview.Row> rows = job.rows().stream().map(row -> {
            var snapshot = row.administrativeSnapshot();
            return new ParticipantImportPreview.Row(row.rowNumber(), row.participantCode(),
                    snapshot == null ? null : snapshot.fullName(), snapshot == null ? null : snapshot.sex(),
                    snapshot == null ? null : snapshot.dateOfBirth(),
                    snapshot == null ? null : snapshot.identificationNumber().value(), row.errorCodes());
        }).toList();
        int valid = (int) rows.stream().filter(ParticipantImportPreview.Row::valid).count();
        return new ParticipantImportPreview(job.id(), job.batchId(), job.status(), rows.size(), valid,
                rows.size() - valid, rows);
    }
}
