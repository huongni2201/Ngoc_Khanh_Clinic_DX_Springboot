package com.ngockhanh.clinic.healthcheck.domain.aggregate;

import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.exception.DomainRuleViolation;

import com.ngockhanh.clinic.healthcheck.domain.exception.DuplicateEmployeeServiceAssignment;
import com.ngockhanh.clinic.healthcheck.domain.exception.ServiceOutsideBatchScope;
import com.ngockhanh.clinic.healthcheck.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthcheck.domain.enums.HealthCheckRecordStatus;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.AdministrativeSnapshot;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.BatchPriceRevision;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HealthCheckBatchEmployee {
    private final UUID id;
    private final UUID batchId;
    private final AdministrativeSnapshot rosterSnapshot;
    private final Map<UUID, HealthCheckBatchEmployeeService> assignments = new HashMap<>();

    public HealthCheckBatchEmployee(UUID batchId, AdministrativeSnapshot rosterSnapshot) {
        this(null, batchId, rosterSnapshot);
    }

    public HealthCheckBatchEmployee(UUID id, UUID batchId, AdministrativeSnapshot rosterSnapshot) {
        if (id == null || batchId == null || rosterSnapshot == null)
            throw new IllegalArgumentException("Invalid batch employee");
        this.id = id;
        this.batchId = batchId;
        this.rosterSnapshot = rosterSnapshot;
    }

    public static HealthCheckBatchEmployee restore(
            UUID id,
            UUID batchId,
            AdministrativeSnapshot snapshot,
            List<HealthCheckBatchEmployeeService> assignments
    ) {
        if (id == null || assignments == null) throw new IllegalArgumentException("Invalid persisted batch employee");
        HealthCheckBatchEmployee employee = new HealthCheckBatchEmployee(id, batchId, snapshot);
        for (HealthCheckBatchEmployeeService assignment : assignments) {
            if (assignment == null || employee.assignments.putIfAbsent(assignment.batchServiceId(), assignment) != null) {
                throw new IllegalArgumentException("Invalid persisted assignment list");
            }
        }
        return employee;
    }

    public void assign(HealthCheckBatch batch, HealthCheckRecord record, HealthCheckBatchService service, UUID serviceRequestId) {
        if (batch == null || batch.id() != batchId || service == null || service.batchId() != batchId
                || batch.service(service.id()) != service) throw new ServiceOutsideBatchScope();
        if (batch.status() != BatchStatus.READY && batch.status() != BatchStatus.IN_PROGRESS) {
            throw new DomainRuleViolation("Batch is not open for service selection");
        }
        if (record == null || id == null || !id.equals(record.batchEmployeeId())
                || record.status() != HealthCheckRecordStatus.ACTIVE || record.actualExaminationDate() == null) {
            throw new DomainRuleViolation("Employee has not checked in to an active health-check record");
        }
        if (serviceRequestId == null) throw new IllegalArgumentException("Missing Service Request");
        if (assignments.putIfAbsent(service.id(), new HealthCheckBatchEmployeeService(service.id(), serviceRequestId, service.negotiatedPrice())) != null) {
            throw new DuplicateEmployeeServiceAssignment();
        }
    }

    public void markBillable(UUID serviceRequestId, Boolean completedSuccessfully) {
        HealthCheckBatchEmployeeService assignment = assignments.values().stream()
                .filter(item -> item.serviceRequestId() == serviceRequestId).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Service Request"));
        assignment.markBillable(completedSuccessfully);
    }

    public void applyPriceRevision(BatchPriceRevision revision) {
        if (revision == null || revision.batchId() != batchId)
            throw new IllegalArgumentException("Price revision for another batch");
        HealthCheckBatchEmployeeService assignment = assignments.get(revision.batchServiceId());
        if (assignment != null) {
            if (assignment.unitPrice().amount().compareTo(revision.oldPrice().amount()) != 0) {
                throw new DomainRuleViolation("Stale batch price revision");
            }
            assignment.reprice(revision.newPrice());
        }
    }

    public HealthCheckBatchEmployeeService assignmentFor(UUID serviceId) {
        return assignments.get(serviceId);
    }

    public UUID id() {
        return id;
    }

    public AdministrativeSnapshot rosterSnapshot() {
        return rosterSnapshot;
    }
}

