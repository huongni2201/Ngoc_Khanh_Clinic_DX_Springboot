package com.ngockhanh.clinic.healthexamination.domain.aggregate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationImportRow;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportType;
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
        if (rows == null) throw new IllegalArgumentException("Invalid persisted import job");
        HealthExaminationImportJob job = new HealthExaminationImportJob(id, batchId, type, status,
                sourceFileAttachmentId, createdByUserId, createdAt, confirmedByUserId, confirmedAt);
        addRows(job, rows);
        return job;
    }

    private static void addRows(HealthExaminationImportJob job, List<HealthExaminationImportRow> rows) {
        for (HealthExaminationImportRow row : rows) {
            if (row == null || job.rows.putIfAbsent(row.rowNumber(), row) != null) {
                throw new IllegalArgumentException("Invalid persisted import row list");
            }
        }
    }

    public void addRow(HealthExaminationImportRow row) {
        if (status != ImportStatus.UPLOADED) throw new DomainRuleViolation("Import rows locked");
        if (row == null) throw new IllegalArgumentException("Missing import row");
        if (rows.putIfAbsent(row.rowNumber(), row) != null) throw new DomainRuleViolation("Duplicate import row");
    }

    public void validate() {
        if (status != ImportStatus.UPLOADED || rows.isEmpty()) {
            throw new DomainRuleViolation("Import cannot be validated");
        }
        status = ImportStatus.VALIDATED;
    }

    public void confirm() {
        if (status != ImportStatus.VALIDATED || rows.values().stream().noneMatch(HealthExaminationImportRow::valid)
                || (type == ImportType.PARTICIPANT_LIST && rows.values().stream()
                .anyMatch(row -> row.valid() && !row.hasAdministrativeIdentity()))
                || (type == ImportType.RESULTS && rows.values().stream()
                .anyMatch(row -> row.valid() && row.serviceRequestId() == null))) {
            throw new DomainRuleViolation("Import has blocking errors");
        }
        status = rows.values().stream().anyMatch(row -> !row.valid())
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
        return rows.values().stream().filter(row -> row.valid() && row.hasAdministrativeIdentity())
                .sorted((left, right) -> Integer.compare(left.rowNumber(), right.rowNumber())).toList();
    }

    public List<HealthExaminationImportRow> confirmableResultRows() {
        if (type != ImportType.RESULTS || !isImportReviewed()) {
            throw new DomainRuleViolation("Result rows are not validated");
        }
        return rows.values().stream().filter(row -> row.valid() && row.serviceRequestId() != null)
                .sorted((left, right) -> Integer.compare(left.rowNumber(), right.rowNumber())).toList();
    }

    private boolean isImportReviewed() {
        return status == ImportStatus.VALIDATED || status == ImportStatus.PARTIAL || status == ImportStatus.CONFIRMED;
    }

    public AggregateId id() { return id; }
    public AggregateId batchId() { return batchId; }
    public ImportType type() { return type; }
    public ImportStatus status() { return status; }
    public AggregateId sourceFileAttachmentId() { return sourceFileAttachmentId; }
    public AggregateId createdByUserId() { return createdByUserId; }
    public AggregateId confirmedByUserId() { return confirmedByUserId; }
    public Instant createdAt() { return createdAt; }
    public Instant confirmedAt() { return confirmedAt; }
    public boolean isConfirmed() { return status == ImportStatus.CONFIRMED || status == ImportStatus.PARTIAL; }

    public List<HealthExaminationImportRow> rows() {
        List<HealthExaminationImportRow> orderedRows = new ArrayList<>(rows.values());
        orderedRows.sort((left, right) -> Integer.compare(left.rowNumber(), right.rowNumber()));
        return List.copyOf(orderedRows);
    }
}
