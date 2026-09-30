package com.ngockhanh.clinic.healthexamination.domain.aggregate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationImportRow;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportType;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.ParticipantImportColumnMapping;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;

public final class HealthExaminationImportJob {
    private final AggregateId id;
    private final AggregateId batchId;
    private final ImportType type;
    private final AggregateId sourceFileAttachmentId;
    private final AggregateId createdByUserId;
    private final Instant createdAt;
    private final Map<Integer, HealthExaminationImportRow> rows = new HashMap<>();
    private ParticipantImportColumnMapping columnMapping;
    private ImportStatus status;
    private AggregateId confirmedByUserId;
    private Instant confirmedAt;

    private HealthExaminationImportJob(AggregateId id, AggregateId batchId, ImportType type,
                                       ImportStatus status, AggregateId sourceFileAttachmentId,
                                       AggregateId createdByUserId, Instant createdAt,
                                       AggregateId confirmedByUserId, Instant confirmedAt) {
        if (id == null || batchId == null || type == null || status == null) {
            throw new IllegalArgumentException("Invalid import job");
        }
        this.id = id;
        this.batchId = batchId;
        this.type = type;
        this.status = status;
        this.sourceFileAttachmentId = sourceFileAttachmentId;
        this.createdByUserId = createdByUserId;
        this.createdAt = createdAt;
        this.confirmedByUserId = confirmedByUserId;
        this.confirmedAt = confirmedAt;
    }

    public static HealthExaminationImportJob create(AggregateId id, AggregateId batchId, ImportType type) {
        return new HealthExaminationImportJob(id, batchId, type, ImportStatus.UPLOADED,
                null, null, null, null, null);
    }

    public static HealthExaminationImportJob create(AggregateId id, AggregateId batchId, ImportType type,
                                                    AggregateId sourceFileAttachmentId,
                                                    AggregateId createdByUserId, Instant createdAt) {
        if (sourceFileAttachmentId == null || createdByUserId == null || createdAt == null) {
            throw new IllegalArgumentException("Import job audit and source metadata are required");
        }
        return new HealthExaminationImportJob(id, batchId, type, ImportStatus.UPLOADED,
                sourceFileAttachmentId, createdByUserId, createdAt, null, null);
    }

    public static HealthExaminationImportJob restore(AggregateId id, AggregateId batchId, ImportType type,
                                                     ImportStatus status, List<HealthExaminationImportRow> rows) {
        if (rows == null) throw new IllegalArgumentException("Invalid persisted import job");
        HealthExaminationImportJob job = new HealthExaminationImportJob(id, batchId, type, status,
                null, null, null, null, null);
        addRows(job, rows);
        return job;
    }

    public static HealthExaminationImportJob restore(AggregateId id, AggregateId batchId, ImportType type,
                                                     ImportStatus status, AggregateId sourceFileAttachmentId,
                                                     AggregateId createdByUserId, Instant createdAt,
                                                     AggregateId confirmedByUserId, Instant confirmedAt,
                                                     List<HealthExaminationImportRow> rows) {
        return restore(id, batchId, type, status, sourceFileAttachmentId, createdByUserId, createdAt,
                confirmedByUserId, confirmedAt, null, rows);
    }

    public static HealthExaminationImportJob restore(AggregateId id, AggregateId batchId, ImportType type,
                                                     ImportStatus status, AggregateId sourceFileAttachmentId,
                                                     AggregateId createdByUserId, Instant createdAt,
                                                     AggregateId confirmedByUserId, Instant confirmedAt,
                                                     ParticipantImportColumnMapping columnMapping,
                                                     List<HealthExaminationImportRow> rows) {
        if (rows == null) throw new IllegalArgumentException("Invalid persisted import job");
        HealthExaminationImportJob job = new HealthExaminationImportJob(id, batchId, type, status,
                sourceFileAttachmentId, createdByUserId, createdAt, confirmedByUserId, confirmedAt);
        job.columnMapping = columnMapping;
        addRows(job, rows);
        return job;
    }

    private static void addRows(HealthExaminationImportJob job, List<HealthExaminationImportRow> rows) {
        for (HealthExaminationImportRow row : rows) {
            if (row == null || job.rows.putIfAbsent(row.getRowNumber(), row) != null) {
                throw new IllegalArgumentException("Invalid persisted import row list");
            }
        }
    }

    public void addRow(HealthExaminationImportRow row) {
        if (status != ImportStatus.UPLOADED) throw new DomainRuleViolation("Import rows locked");
        if (row == null) throw new IllegalArgumentException("Missing import row");
        if (rows.putIfAbsent(row.getRowNumber(), row) != null) throw new DomainRuleViolation("Duplicate import row");
    }

    public void mapColumns(ParticipantImportColumnMapping mapping) {
        if (type != ImportType.PARTICIPANT_LIST || status != ImportStatus.UPLOADED || mapping == null) {
            throw new DomainRuleViolation("Import columns cannot be mapped");
        }
        columnMapping = mapping;
    }

    public void validate() {
        if (status != ImportStatus.UPLOADED || rows.isEmpty()
                || (type == ImportType.PARTICIPANT_LIST && columnMapping == null)) {
            throw new DomainRuleViolation("Import cannot be validated");
        }
        status = ImportStatus.VALIDATED;
    }

    public void replaceValidatedRoster(ParticipantImportColumnMapping mapping,
                                       List<HealthExaminationImportRow> validatedRows) {
        if (type != ImportType.PARTICIPANT_LIST || status != ImportStatus.VALIDATED
                || mapping == null || validatedRows == null || validatedRows.isEmpty()) {
            throw new DomainRuleViolation("Validated roster cannot be refreshed");
        }
        Map<Integer, HealthExaminationImportRow> replacement = new HashMap<>();
        for (HealthExaminationImportRow row : validatedRows) {
            if (row == null || replacement.putIfAbsent(row.getRowNumber(), row) != null) {
                throw new DomainRuleViolation("Validated roster contains duplicate rows");
            }
        }
        rows.clear();
        rows.putAll(replacement);
        columnMapping = mapping;
    }

    public void cancel() {
        if (status == ImportStatus.CONFIRMED || status == ImportStatus.PARTIAL
                || status == ImportStatus.FAILED || status == ImportStatus.CANCELED) {
            throw new DomainRuleViolation("Import job cannot be canceled in its current state");
        }
        status = ImportStatus.CANCELED;
    }

    public void confirm() {
        if (status != ImportStatus.VALIDATED || rows.values().stream().noneMatch(HealthExaminationImportRow::isValid)
                || (type == ImportType.PARTICIPANT_LIST && rows.values().stream()
                .anyMatch(row -> !row.isValid()))
                || (type == ImportType.PARTICIPANT_LIST && rows.values().stream()
                .anyMatch(row -> row.isValid() && !row.hasAdministrativeIdentity()))
                || (type == ImportType.RESULTS && rows.values().stream()
                .anyMatch(row -> row.isValid() && row.getServiceRequestId() == null))) {
            throw new DomainRuleViolation("Import has blocking errors");
        }
        status = rows.values().stream().anyMatch(row -> !row.isValid())
                ? ImportStatus.PARTIAL : ImportStatus.CONFIRMED;
    }

    public void confirm(AggregateId actorUserId, Instant confirmedAt) {
        if (actorUserId == null || confirmedAt == null) {
            throw new IllegalArgumentException("Confirmation audit is required");
        }
        confirm();
        this.confirmedByUserId = actorUserId;
        this.confirmedAt = confirmedAt;
    }

    public List<HealthExaminationImportRow> confirmableRosterRows() {
        if (type != ImportType.PARTICIPANT_LIST || !isImportReviewed()) {
            throw new DomainRuleViolation("Roster rows are not validated");
        }
        return rows.values().stream().filter(row -> row.isValid() && row.hasAdministrativeIdentity())
                .sorted((left, right) -> Integer.compare(left.getRowNumber(), right.getRowNumber())).toList();
    }

    public List<HealthExaminationImportRow> confirmableResultRows() {
        if (type != ImportType.RESULTS || !isImportReviewed()) {
            throw new DomainRuleViolation("Result rows are not validated");
        }
        return rows.values().stream().filter(row -> row.isValid() && row.getServiceRequestId() != null)
                .sorted((left, right) -> Integer.compare(left.getRowNumber(), right.getRowNumber())).toList();
    }

    private boolean isImportReviewed() {
        return status == ImportStatus.VALIDATED || status == ImportStatus.PARTIAL || status == ImportStatus.CONFIRMED;
    }

    public AggregateId id() { return id; }
    public AggregateId batchId() { return batchId; }
    public ImportType type() { return type; }
    public ImportStatus status() { return status; }
    public ParticipantImportColumnMapping columnMapping() { return columnMapping; }
    public AggregateId sourceFileAttachmentId() { return sourceFileAttachmentId; }
    public AggregateId createdByUserId() { return createdByUserId; }
    public AggregateId confirmedByUserId() { return confirmedByUserId; }
    public Instant createdAt() { return createdAt; }
    public Instant confirmedAt() { return confirmedAt; }
    public boolean isConfirmed() { return status == ImportStatus.CONFIRMED || status == ImportStatus.PARTIAL; }

    public List<HealthExaminationImportRow> rows() {
        List<HealthExaminationImportRow> orderedRows = new ArrayList<>(rows.values());
        orderedRows.sort((left, right) -> Integer.compare(left.getRowNumber(), right.getRowNumber()));
        return List.copyOf(orderedRows);
    }
}
