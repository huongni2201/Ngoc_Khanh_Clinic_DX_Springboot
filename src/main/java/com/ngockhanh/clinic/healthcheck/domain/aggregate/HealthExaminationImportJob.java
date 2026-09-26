package com.ngockhanh.clinic.healthcheck.domain.aggregate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.enums.ImportStatus;
import com.ngockhanh.clinic.healthcheck.domain.enums.ImportType;
import com.ngockhanh.clinic.healthcheck.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthcheck.domain.entity.HealthExaminationImportRow;

public final class HealthExaminationImportJob {
    private final UUID id;
    private final UUID batchId;
    private final ImportType type;
    private final Map<Integer, HealthExaminationImportRow> rows = new HashMap<>();
    private ImportStatus status;

    private HealthExaminationImportJob(UUID id, UUID batchId, ImportType type, ImportStatus status) {
        if (id == null || batchId == null || type == null || status == null) {
            throw new IllegalArgumentException("Invalid import job");
        }
        this.id = id;
        this.batchId = batchId;
        this.type = type;
        this.status = status;
    }

    public static HealthExaminationImportJob create(UUID id, UUID batchId, ImportType type) {
        return new HealthExaminationImportJob(id, batchId, type, ImportStatus.UPLOADED);
    }

    public static HealthExaminationImportJob restore(UUID id, UUID batchId, ImportType type, ImportStatus status,
                                               List<HealthExaminationImportRow> rows) {
        if (rows == null) throw new IllegalArgumentException("Invalid persisted import job");
        HealthExaminationImportJob job = new HealthExaminationImportJob(id, batchId, type, status);
        for (HealthExaminationImportRow row : rows) {
            if (row == null || job.rows.putIfAbsent(row.rowNumber(), row) != null) {
                throw new IllegalArgumentException("Invalid persisted import row list");
            }
        }
        return job;
    }

    public void addRow(HealthExaminationImportRow row) {
        if (status != ImportStatus.UPLOADED) throw new DomainRuleViolation("Import rows locked");
        if (row == null) throw new IllegalArgumentException("Missing import row");
        if (rows.putIfAbsent(row.rowNumber(), row) != null) throw new DomainRuleViolation("Duplicate import row");
    }

    public void validate() {
        if (status != ImportStatus.UPLOADED || rows.isEmpty()) throw new DomainRuleViolation("Import cannot be validated");
        status = ImportStatus.VALIDATED;
    }

    public void confirm() {
        if (status != ImportStatus.VALIDATED || rows.values().stream().noneMatch(row -> row.valid())
                || (type == ImportType.PARTICIPANT_LIST && rows.values().stream()
                    .anyMatch(row -> row.valid() && row.administrativeSnapshot() == null))
                || (type == ImportType.RESULTS && rows.values().stream()
                    .anyMatch(row -> row.valid() && row.serviceRequestId() == null))) {
            throw new DomainRuleViolation("Import has blocking errors");
        }
        status = rows.values().stream().anyMatch(row -> !row.valid()) ? ImportStatus.PARTIAL : ImportStatus.CONFIRMED;
    }

    public List<HealthExaminationImportRow> confirmableRosterRows() {
        if (type != ImportType.PARTICIPANT_LIST || !isImportReviewed()) {
            throw new DomainRuleViolation("Roster rows are not validated");
        }
        return rows.values().stream().filter(row -> row.valid() && row.administrativeSnapshot() != null)
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

    public UUID id() { return id; }
    public UUID batchId() { return batchId; }
    public ImportType type() { return type; }
    public ImportStatus status() { return status; }
    public boolean isConfirmed() { return status == ImportStatus.CONFIRMED || status == ImportStatus.PARTIAL; }
    public List<HealthExaminationImportRow> rows() {
        List<HealthExaminationImportRow> orderedRows = new ArrayList<>(rows.values());
        orderedRows.sort((left, right) -> Integer.compare(left.rowNumber(), right.rowNumber()));
        return List.copyOf(orderedRows);
    }
}
